/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.AntFilesProvider;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.ReflectedProject;
import com.intellij.lang.ant.config.impl.AntResourcesClassLoader;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.xml.XmlName;
import gnu.trove.THashMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Storage for user-defined tasks and data types
 * parsed from ant files
 * @author Eugene Zhuravlev
 */
public class CustomAntElementsRegistry {

  public static ThreadLocal<Boolean> ourIsBuildingClasspathForCustomTagLoading = new ThreadLocal<Boolean>() {
    protected Boolean initialValue() {
      return Boolean.FALSE;
    }
  };
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.dom.CustomAntElementsRegistry");
  private static final Key<CustomAntElementsRegistry> REGISTRY_KEY = Key.create("_custom_element_registry_");

  private final Map<XmlName, ClassProvider> myCustomElements = new THashMap<>();
  private final Map<AntDomNamedElement, String> myTypeDefErrors = new THashMap<>();
  private final Map<XmlName, AntDomNamedElement> myDeclarations = new THashMap<>();
  private final Map<String, ClassLoader> myNamedLoaders = new THashMap<>();

  private CustomAntElementsRegistry(final AntDomProject antProject) {
    antProject.accept(new CustomTagDefinitionFinder(antProject));
  }

  public static CustomAntElementsRegistry getInstance(AntDomProject antProject) {
    CustomAntElementsRegistry registry = antProject.getContextAntProject().getUserData(REGISTRY_KEY);
    if (registry == null) {
      registry = new CustomAntElementsRegistry(antProject);
      antProject.putUserData(REGISTRY_KEY, registry);
    }
    return registry;
  }

  @NotNull
  public Set<XmlName> getCompletionVariants(AntDomElement parentElement) {
    if (parentElement instanceof AntDomCustomElement) {
      // this case is already handled in AntDomExtender when defining children
      return Collections.emptySet(); 
    }
    final Set<XmlName> result = new HashSet<>();
    
    final Pair<AntDomMacroDef, AntDomScriptDef> contextMacroOrScriptDef = getContextMacroOrScriptDef(parentElement);
    final AntDomMacroDef restrictToMacroDef = contextMacroOrScriptDef != null? contextMacroOrScriptDef.getFirst() : null;
    final AntDomScriptDef restrictToScriptDef = contextMacroOrScriptDef != null? contextMacroOrScriptDef.getSecond() : null;
    final boolean parentIsDataType = parentElement.isDataType();

    for (final XmlName xmlName : myCustomElements.keySet()) {
      final AntDomNamedElement declaringElement = myDeclarations.get(xmlName);
      if (declaringElement instanceof AntDomMacrodefElement) {
        if (restrictToMacroDef == null || !restrictToMacroDef.equals(declaringElement.getParentOfType(AntDomMacroDef.class, true))) {
          continue;
        }
      }
      else if (declaringElement instanceof AntDomScriptdefElement) {
        if (restrictToScriptDef == null || !restrictToScriptDef.equals(declaringElement.getParentOfType(AntDomScriptDef.class, true))) {
          continue;
        }
      }

      if (declaringElement != null) {
        if (declaringElement.equals(restrictToMacroDef) || declaringElement.equals(restrictToScriptDef)) {
          continue;
        }
      }

      if (parentIsDataType) {
        if (declaringElement instanceof AntDomMacroDef || declaringElement instanceof AntDomScriptDef || declaringElement instanceof AntDomTaskdef) {
          continue;
        }
        if (declaringElement instanceof AntDomTypeDef) {
          final AntDomTypeDef typedef = (AntDomTypeDef)declaringElement;
          final Class clazz = lookupClass(xmlName);
          if (clazz != null && typedef.isTask(clazz)) {
            continue;                                                           
          }
        }
      }
      
      result.add(xmlName);
    }
    return result;
  }

  @Nullable
  private Pair<AntDomMacroDef, AntDomScriptDef> getContextMacroOrScriptDef(AntDomElement element) {
    final AntDomMacroDef macrodef = element.getParentOfType(AntDomMacroDef.class, false);
    if (macrodef != null) {
      return new Pair<>(macrodef, null);
    }
    for (AntDomCustomElement custom = element.getParentOfType(AntDomCustomElement.class, false); custom != null; custom = custom.getParentOfType(AntDomCustomElement.class, true)) {
      final AntDomNamedElement declaring = getDeclaringElement(custom.getXmlName());
      if (declaring instanceof AntDomMacroDef) {
        return new Pair<>((AntDomMacroDef)declaring, null);
      }
      else if (declaring instanceof AntDomScriptDef) {
        return new Pair<>(null, (AntDomScriptDef)declaring);
      }
    }
    return null;
  }

  @Nullable
  public AntDomElement findDeclaringElement(final AntDomElement parentElement, final XmlName customElementName) {
    final AntDomElement declaration = myDeclarations.get(customElementName);
    if (declaration == null) {
      return null;
    }
    
    if (declaration instanceof AntDomMacrodefElement) {
      final Pair<AntDomMacroDef, AntDomScriptDef> contextMacroOrScriptDef = getContextMacroOrScriptDef(parentElement);
      final AntDomMacroDef macrodefUsed = contextMacroOrScriptDef != null? contextMacroOrScriptDef.getFirst() : null;
      if (macrodefUsed == null || !macrodefUsed.equals(declaration.getParentOfType(AntDomMacroDef.class, true))) {
        return null;
      }
    }
    else if (declaration instanceof AntDomScriptdefElement) {
      final Pair<AntDomMacroDef, AntDomScriptDef> contextMacroOrScriptDef = getContextMacroOrScriptDef(parentElement);
      final AntDomScriptDef scriptDefUsed = contextMacroOrScriptDef != null? contextMacroOrScriptDef.getSecond() : null;
      if (scriptDefUsed == null || !scriptDefUsed.equals(declaration.getParentOfType(AntDomScriptDef.class, true))) {
        return null;
      }
    }
    
    return declaration;
  }

  public AntDomNamedElement getDeclaringElement(XmlName customElementName) {
    return myDeclarations.get(customElementName);
  }
  
  @Nullable
  public Class lookupClass(XmlName xmlName) {
    final ClassProvider provider = myCustomElements.get(xmlName);
    return provider == null ? null : provider.lookupClass();
  }

  @Nullable
  public String lookupError(XmlName xmlName) {
    final ClassProvider provider = myCustomElements.get(xmlName);
    return provider == null ? null : provider.getError();
  }

  public boolean hasTypeLoadingErrors(AntDomTypeDef typedef) {
    final String generalError = myTypeDefErrors.get(typedef);
    if (generalError != null) {
      return true;
    }
    return StreamEx.ofKeys(myDeclarations, typedef::equals).anyMatch(name -> lookupError(name) != null);
  }

  public List<String> getTypeLoadingErrors(AntDomTypeDef typedef) {
    final String generalError = myTypeDefErrors.get(typedef);
    if (generalError != null) {
      return Collections.singletonList(generalError);
    }
    List<String> errors = null;
    for (Map.Entry<XmlName, AntDomNamedElement> entry : myDeclarations.entrySet()) {
      if (typedef.equals(entry.getValue())) {
        final String err = lookupError(entry.getKey());
        if (err != null)  {
          if (errors == null) {
            errors = new ArrayList<>();
          }
          errors.add(err);
        }
      }
    }
    return errors == null ? Collections.emptyList() : errors;
  }
  
  private void rememberNamedClassLoader(AntDomCustomClasspathComponent typedef, AntDomProject antProject) {
    final String loaderRef = typedef.getLoaderRef().getStringValue();
    if (loaderRef != null) {
      if (!myNamedLoaders.containsKey(loaderRef)) {
        myNamedLoaders.put(loaderRef, createClassLoader(collectUrls(typedef), antProject));
      }
    }
  }

  @NotNull
  private ClassLoader getClassLoader(AntDomCustomClasspathComponent customComponent, AntDomProject antProject) {
    final String loaderRef = customComponent.getLoaderRef().getStringValue();
    if (loaderRef != null) {
      final ClassLoader loader = myNamedLoaders.get(loaderRef);
      if (loader != null) {
        return loader;
      }
    }
    return createClassLoader(collectUrls(customComponent), antProject);
  }

  @Nullable
  public static PsiFile loadContentAsFile(PsiFile originalFile, LanguageFileType fileType) {
    final VirtualFile vFile = originalFile.getVirtualFile();
    if (vFile == null) {
      return null;
    }
    try {
      return loadContentAsFile(originalFile.getProject(), vFile.getInputStream(), fileType);
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return null;
  }

  public static PsiFile loadContentAsFile(Project project, InputStream stream, LanguageFileType fileType) throws IOException {
    final StringBuilder builder = new StringBuilder();
    try {
      int nextByte;
      while ((nextByte = stream.read()) >= 0) {
        builder.append((char)nextByte);
      }
    }
    finally {
      stream.close();
    }
    final PsiFileFactory factory = PsiFileFactory.getInstance(project);
    return factory.createFileFromText("_ant_dummy__." + fileType.getDefaultExtension(), fileType, builder, LocalTimeCounter.currentTime(), false, false);
  }

  private void addCustomDefinition(@NotNull AntDomNamedElement declaringTag, String customTagName, String nsUri, ClassProvider classProvider) {
    final XmlName xmlName = new XmlName(customTagName, nsUri == null? "" : nsUri);
    myCustomElements.put(xmlName, classProvider);
    myDeclarations.put(xmlName, declaringTag);
  }

  private static PsiFile createDummyFile(@NonNls final String name, final LanguageFileType type, final CharSequence str, Project project) {
    return PsiFileFactory.getInstance(project).createFileFromText(name, type, str, LocalTimeCounter.currentTime(), false, false);
  }

  private static boolean isXmlFormat(AntDomTypeDef typedef, @NotNull final String resourceOrFileName) {
    final String format = typedef.getFormat().getStringValue();
    if (format != null) {
      return "xml".equalsIgnoreCase(format);
    }
    return StringUtil.endsWithIgnoreCase(resourceOrFileName, ".xml");
  }

  @NotNull
  public static ClassLoader createClassLoader(final List<URL> urls, final AntDomProject antProject) {
    final ClassLoader parentLoader = antProject.getClassLoader();
    if (urls.size() == 0) {
      return parentLoader;
    }
    return new AntResourcesClassLoader(urls, parentLoader, false, false);
  }

  public static List<URL> collectUrls(AntDomClasspathElement typedef) {
    boolean cleanupNeeded = false;
    if (!ourIsBuildingClasspathForCustomTagLoading.get()) {
      ourIsBuildingClasspathForCustomTagLoading.set(Boolean.TRUE);
      cleanupNeeded = true;
    }

    try {
      final List<URL> urls = new ArrayList<>();
      // check classpath attribute
      final List<File> cpFiles = typedef.getClasspath().getValue();
      if (cpFiles != null) {
        for (File file : cpFiles) {
          try {
            urls.add(toLocalURL(file));
          }
          catch (MalformedURLException ignored) {
            LOG.info(ignored);
          }
        }
      }

      final HashSet<AntFilesProvider> processed = new HashSet<>();
      final AntDomElement referencedPath = typedef.getClasspathRef().getValue();
      if (referencedPath instanceof AntFilesProvider) {
        for (File cpFile : ((AntFilesProvider)referencedPath).getFiles(processed)) {
          try {
            urls.add(toLocalURL(cpFile));
          }
          catch (MalformedURLException ignored) {
            LOG.info(ignored);
          }
        }
      }
      // check nested elements
      
      for (final Iterator<AntDomElement> it = typedef.getAntChildrenIterator(); it.hasNext();) {
        AntDomElement child = it.next();
        if (child instanceof AntFilesProvider) {
          for (File cpFile : ((AntFilesProvider)child).getFiles(processed)) {
            try {
              urls.add(toLocalURL(cpFile));
            }
            catch (MalformedURLException ignored) {
              LOG.info(ignored);
            }
          }
        }
      }

      return urls;
    }
    finally {
      if (cleanupNeeded) {
        ourIsBuildingClasspathForCustomTagLoading.remove();
      }
    }
  }

  private static URL toLocalURL(final File file) throws MalformedURLException {
    return file.toURI().toURL();
  }

  private class CustomTagDefinitionFinder extends AntDomRecursiveVisitor {
    private final Set<AntDomElement> myElementsOnThePath = new HashSet<>();
    private final Set<String> processedAntlibs = new HashSet<>();
    private final AntDomProject myAntProject;

    public CustomTagDefinitionFinder(AntDomProject antProject) {
      myAntProject = antProject;
    }

    public void visitAntDomElement(AntDomElement element) {
      if (element instanceof AntDomCustomElement || myElementsOnThePath.contains(element)) {
        return; // avoid stack overflow
      }
      myElementsOnThePath.add(element);
      try {
        final XmlTag tag = element.getXmlTag();
        if (tag != null) {
          final String[] uris = tag.knownNamespaces();
          for (String uri : uris) {
            if (!processedAntlibs.contains(uri)) {
              processedAntlibs.add(uri);
              final String antLibResource = AntDomAntlib.toAntlibResource(uri);
              if (antLibResource != null) {
                final XmlElement xmlElement = element.getXmlElement();
                if (xmlElement != null) {
                  final ClassLoader loader = myAntProject.getClassLoader();
                  final InputStream stream = loader.getResourceAsStream(antLibResource);
                  if (stream != null) {
                    try {
                      final XmlFile xmlFile = (XmlFile)loadContentAsFile(xmlElement.getProject(), stream, StdFileTypes.XML);
                      if (xmlFile != null) {
                        loadDefinitionsFromAntlib(xmlFile, uri, loader, null, myAntProject);
                      }
                    }
                    catch (IOException e) {
                      LOG.info(e);
                    }
                  }
                }
              }
            }
          }
        }
        super.visitAntDomElement(element);
      }
      finally {
        myElementsOnThePath.remove(element);
      }
    }

    public void visitMacroDef(AntDomMacroDef macrodef) {
      final String customTagName = macrodef.getName().getStringValue();
      if (customTagName != null) {
        final String nsUri = macrodef.getUri().getStringValue();
        addCustomDefinition(macrodef, customTagName, nsUri, ClassProvider.EMPTY);
        for (AntDomMacrodefElement element : macrodef.getMacroElements()) {
          final String customSubTagName = element.getName().getStringValue();
          if (customSubTagName != null) {
            addCustomDefinition(element, customSubTagName, nsUri, ClassProvider.EMPTY);
          }
        }
      }
    }

    public void visitScriptDef(AntDomScriptDef scriptdef) {
      final String customTagName = scriptdef.getName().getStringValue();
      if (customTagName != null) {
        final String nsUri = scriptdef.getUri().getStringValue();
        final ClassLoader classLoader = getClassLoader(scriptdef, myAntProject);
        // register the scriptdef
        addCustomDefinition(scriptdef, customTagName, nsUri, ClassProvider.EMPTY);
        // registering nested elements
        ReflectedProject reflectedProject = null;
        for (AntDomScriptdefElement element : scriptdef.getScriptdefElements()) {
          final String customSubTagName = element.getName().getStringValue();
          if (customSubTagName != null) {
            final String classname = element.getClassname().getStringValue();
            if (classname != null) {
              addCustomDefinition(element, customTagName, nsUri, ClassProvider.create(classname, classLoader));
            }
            else {
              Class clazz = null;
              final String typeName = element.getElementType().getStringValue();
              if (typeName != null) {
                clazz = lookupClass(new XmlName(typeName));
                if (clazz == null) {
                  if (reflectedProject == null) { // lazy init
                    reflectedProject = ReflectedProject.getProject(myAntProject.getClassLoader());
                  }
                  final Hashtable<String, Class> coreTasks = reflectedProject.getTaskDefinitions();
                  if (coreTasks != null) {
                    clazz = coreTasks.get(typeName);
                  }
                  if (clazz == null) {
                    final Hashtable<String, Class> coreTypes = reflectedProject.getDataTypeDefinitions();
                    if (coreTypes != null) {
                      clazz = coreTypes.get(typeName);
                    }
                  }
                }
              }
              addCustomDefinition(element, customSubTagName, nsUri, ClassProvider.create(clazz));
            }
          }
        }
      }
    }

    public void visitPresetDef(AntDomPresetDef presetdef) {
      final String customTagName = presetdef.getName().getStringValue();
      if (customTagName != null) {
        final String nsUri = presetdef.getUri().getStringValue();
        addCustomDefinition(presetdef, customTagName, nsUri, ClassProvider.EMPTY);
      }
    }

    public void visitTypeDef(AntDomTypeDef typedef) {
      // if loaderRef attribute is specified, make sure the loader is built and stored
      rememberNamedClassLoader(typedef, myAntProject);
      defineCustomElements(typedef, myAntProject);
    }

    public void visitInclude(AntDomInclude includeTag) {
      processInclude(includeTag);
    }

    public void visitImport(AntDomImport importTag) {
      processInclude(importTag);
    }

    private void processInclude(AntDomIncludingDirective directive) {
      final PsiFileSystemItem item = directive.getFile().getValue();
      if (item instanceof PsiFile) {
        final AntDomProject slaveProject = AntSupport.getAntDomProject((PsiFile)item);
        if (slaveProject != null) {
          slaveProject.accept(this);
        }
      }
    }

    private void defineCustomElements(AntDomTypeDef typedef, final AntDomProject antProject) {
      final String uri = typedef.getUri().getStringValue();
      final String customTagName = typedef.getName().getStringValue();
      final String classname = typedef.getClassName().getStringValue();

      if (classname != null && customTagName != null) {
        addCustomDefinition(typedef, customTagName, uri, ClassProvider.create(classname, getClassLoader(typedef, antProject)));
      }
      else {
        defineCustomElementsFromResources(typedef, uri, antProject, null);
      }
    }

    private void defineCustomElementsFromResources(AntDomTypeDef typedef, final String uri, AntDomProject antProject, ClassLoader loader) {
      final XmlElement xmlElement = antProject.getXmlElement();
      final Project project = xmlElement != null? xmlElement.getProject() : null;
      if (project == null) {
        return;
      }
      XmlFile xmlFile = null;
      PropertiesFile propFile = null;

      final String resource = typedef.getResource().getStringValue();
      if (resource != null) {
        if (loader == null) {
          loader = getClassLoader(typedef, antProject);
        }
        final InputStream stream = loader.getResourceAsStream(resource);
        if (stream != null) {
          try {
            if (isXmlFormat(typedef, resource)) {
              xmlFile = (XmlFile)loadContentAsFile(project, stream, StdFileTypes.XML);
            }
            else {
              propFile = (PropertiesFile)loadContentAsFile(project, stream, StdFileTypes.PROPERTIES);
            }
          }
          catch (IOException e) {
            LOG.info(e);
          }
        }
        else {
          myTypeDefErrors.put(typedef, "Resource \"" + resource + "\" not found in the classpath");
        }
      }
      else {
        final PsiFileSystemItem file = typedef.getFile().getValue();
        if (file instanceof PsiFile) {
          if (isXmlFormat(typedef, file.getName())) {
            xmlFile = file instanceof XmlFile ? (XmlFile)file : (XmlFile)loadContentAsFile((PsiFile)file, StdFileTypes.XML);
          }
          else { // assume properties format
            propFile = file instanceof PropertiesFile ? (PropertiesFile)file : (PropertiesFile)loadContentAsFile((PsiFile)file, StdFileTypes.PROPERTIES);
          }
        }
      }

      if (propFile != null) {
        if (loader == null) { // if not initialized yet
          loader = getClassLoader(typedef, antProject);
        }
        for (final IProperty property : propFile.getProperties()) {
          addCustomDefinition(typedef, property.getUnescapedKey(), uri, ClassProvider.create(property.getUnescapedValue(), loader));
        }
      }

      if (xmlFile != null) {
        if (loader == null) { // if not initialized yet
          loader = getClassLoader(typedef, antProject);
        }
        loadDefinitionsFromAntlib(xmlFile, uri, loader, typedef, antProject);
      }
    }

    private void loadDefinitionsFromAntlib(XmlFile xmlFile, String uri, ClassLoader loader, @Nullable AntDomTypeDef typedef, AntDomProject antProject) {
      final AntDomAntlib antLib = AntSupport.getAntLib(xmlFile);
      if (antLib != null) {
        final List<AntDomTypeDef> defs = new ArrayList<>();
        defs.addAll(antLib.getTaskdefs());
        defs.addAll(antLib.getTypedefs());
        if (!defs.isEmpty()) {
          for (AntDomTypeDef def : defs) {
            final String tagName = def.getName().getStringValue();
            final String className = def.getClassName().getStringValue();
            if (tagName != null && className != null) {
              AntDomNamedElement declaringElement = typedef != null? typedef : def;
              addCustomDefinition(declaringElement, tagName, uri, ClassProvider.create(className, loader));
            }
            else {
              defineCustomElementsFromResources(def, uri, antProject, loader);
            }
          }
        }
      }
    }

  }
}
