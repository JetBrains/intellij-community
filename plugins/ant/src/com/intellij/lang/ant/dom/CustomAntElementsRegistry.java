/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.impl.AntResourcesClassLoader;
import com.intellij.lang.ant.psi.AntFilesProvider;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.xml.XmlName;
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
 *         Date: Jul 1, 2010
 */
public class CustomAntElementsRegistry {

  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.dom.CustomAntElementsRegistry");
  private static final Key<CustomAntElementsRegistry> REGISTRY_KEY = Key.create("_custom_element_registry_");

  private final Map<XmlName, Class> myCustomElements = new HashMap<XmlName, Class>();
  private final Map<XmlName, String> myErrors = new HashMap<XmlName, String>();
  private final Map<XmlName, AntDomElement> myDeclarations = new HashMap<XmlName, AntDomElement>();
  private final Map<String, ClassLoader> myNamedLoaders = new HashMap<String, ClassLoader>();

  private CustomAntElementsRegistry(final AntDomProject antProject) {
    antProject.accept(new BaseVisitor() {
      public void visitTypeDef(AntDomTypeDef typedef) {
        // if loaderRef attribute is specified, make sure the loader is built and stored
        rememberNamedClassLoader(typedef, antProject);
        defineCustomElements(typedef, antProject);
      }
    });
  }

  public static CustomAntElementsRegistry getInstance(AntDomProject antProject) {
    // todo: choose topmost context project
    CustomAntElementsRegistry registry = getContextProject(antProject).getUserData(REGISTRY_KEY);
    if (registry == null) {
      registry = new CustomAntElementsRegistry(antProject);
      antProject.putUserData(REGISTRY_KEY, registry);
    }
    return registry;
  }

  @NotNull
  private static AntDomProject getContextProject(@NotNull AntDomProject antProject) {
    return antProject;
  }

  @NotNull
  public Set<XmlName> getCompletionVariants(AntDomElement parentElement) {
    return Collections.unmodifiableSet(myCustomElements.keySet());
  }

  @Nullable
  public AntDomElement findDeclaringElement(final AntDomElement parentElement, final XmlName customElementName) {
    final AntDomElement declaration = myDeclarations.get(customElementName);
    return declaration != null? declaration : null;
  }

  @Nullable
  public Class lookupClass(XmlName xmlName) {
    return myCustomElements.get(xmlName);
  }

  @Nullable
  public String lookupError(XmlName xmlName) {
    return myErrors.get(xmlName);
  }

  private static class BaseVisitor extends AntDomRecursiveVisitor {
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
  }

  private void defineCustomElements(AntDomTypeDef typedef, final AntDomProject antProject) {
    final String uri = typedef.getUri().getStringValue();

    final String customTagName = typedef.getName().getStringValue();
    final String classname = typedef.getClassName().getStringValue();
    if (classname != null && customTagName != null) {
      registerElement(typedef, customTagName, uri, classname, getClassLoader(typedef, antProject));
    }
    else {
      final XmlElement xmlElement = antProject.getXmlElement();
      final Project project = xmlElement != null? xmlElement.getProject() : null;
      if (project != null) {
        final String resource = typedef.getResource().getStringValue();
        if (resource != null) {
          final ClassLoader loader = getClassLoader(typedef, antProject);
          if (loader != null) {
            final InputStream stream = loader.getResourceAsStream(resource);
            if (stream != null) {
              loadFromStream(typedef, uri, resource, loader, stream, project);
            }
          }
        }
        else {
          final PsiFileSystemItem file = typedef.getFile().getValue();
          if (file instanceof PsiFile) {
            final VirtualFile vf = file.getVirtualFile();
            if (vf != null) {
              try {
                final InputStream stream = vf.getInputStream();
                if (stream != null) {
                  loadFromStream(typedef, uri, file.getName(), getClassLoader(typedef, antProject), stream, project);
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

  private void rememberNamedClassLoader(AntDomTypeDef typedef, AntDomProject antProject) {
    final String loaderRef = typedef.getLoaderRef().getStringValue();
    if (loaderRef != null) {
      if (!myNamedLoaders.containsKey(loaderRef)) {
        myNamedLoaders.put(loaderRef, createClassLoader(collectUrls(typedef), antProject));
      }
    }
  }

  @Nullable
  private ClassLoader getClassLoader(AntDomTypeDef typedef, AntDomProject antProject) {
    final String loaderRef = typedef.getLoaderRef().getStringValue();
    if (loaderRef != null && myNamedLoaders.containsKey(loaderRef)) {
      return myNamedLoaders.get(loaderRef);
    }
    return createClassLoader(collectUrls(typedef), antProject);
  }

  private void loadFromStream(AntDomTypeDef typedef, String nsPrefix, String resource, ClassLoader loader, InputStream stream, final Project project) {
    if (isXmlFormat(typedef, resource)) {
      // todo
      //loadAntlibStream(stream, nsPrefix, project);
    }
    else {
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        int nextByte;
        try {
          while ((nextByte = stream.read()) >= 0) {
            builder.append((char)nextByte);
          }
        }
        finally {
          stream.close();
        }
        final PropertiesFile propFile = (PropertiesFile)createDummyFile("dummy.properties", StdFileTypes.PROPERTIES, builder, project);
        for (final Property property : propFile.getProperties()) {
          registerElement(typedef, property.getUnescapedKey(), nsPrefix, property.getValue(), loader);
        }
      }
      catch (IOException e) {
        LOG.info(e);
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }
    }
  }

  /*
  static void loadAntlibStream(@NotNull final InputStream antlibStream, final String nsPrefix, Project project) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      int nextByte;
      while ((nextByte = antlibStream.read()) >= 0) {
        builder.append((char)nextByte);
      }
      antlibStream.close();
      final XmlFile xmlFile = (XmlFile)createDummyFile("dummy.xml", StdFileTypes.XML, builder, project);
      final XmlDocument document = xmlFile.getDocument();
      if (document != null) {
        final XmlTag rootTag = document.getRootTag();
        if (rootTag == null) return;
        for (final XmlTag tag : rootTag.getSubTags()) {
          if (nsPrefix != null && nsPrefix.length() > 0) {
            try {
              tag.setName(nsPrefix + ':' + tag.getLocalName());
            }
            catch (IncorrectOperationException e) {
              continue;
            }
          }
          final AntElement newElement = AntElementFactory.createAntElement(element, tag);
          if (newElement instanceof AntTypeDef) {
            for (final AntTypeDefinition def : ((AntTypeDef)newElement).getDefinitions()) {
              if (element instanceof AntTypeDefImpl) {
                final AntTypeDefImpl td = ((AntTypeDefImpl)element);
                final AntTypeDefinition[] defs = td.myNewDefinitions != null ? td.myNewDefinitions : AntTypeDefinition.EMPTY_ARRAY;
                td.myNewDefinitions = ArrayUtil.append(defs, def);
              }
            }
          }
        }
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }
  */


  private void registerElement(AntDomTypeDef typedef, String customTagName, String nsUri, String classname, ClassLoader loader) {
    Class clazz = null;
    String error = "";
    try {
      clazz = loader.loadClass(classname);
    }
    catch (ClassNotFoundException e) {
      error = e.getMessage();
      clazz = null;
    }
    catch (NoClassDefFoundError e) {
      error = e.getMessage();
      clazz = null;
    }
    catch (UnsupportedClassVersionError e) {
      error = e.getMessage();
      clazz = null;
    }
    final XmlName xmlName = new XmlName(customTagName, nsUri == null? "" : nsUri);
    if (clazz != null) {
      myCustomElements.put(xmlName, clazz);
      myDeclarations.put(xmlName, typedef);
    }
    else {
      myErrors.put(xmlName, error);
    }
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

  @Nullable
  private static ClassLoader createClassLoader(final List<URL> urls, final AntDomProject antProject) {
    final ClassLoader parentLoader = antProject.getClassLoader();
    if (urls.size() == 0) {
      return parentLoader;
    }
    return new AntResourcesClassLoader(urls, parentLoader, false, false);
  }

  private static List<URL> collectUrls(AntDomTypeDef typedef) {
    final List<URL> urls = new ArrayList<URL>();
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

    final HashSet<AntFilesProvider> processed = new HashSet<AntFilesProvider>();
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
    for (AntDomElement child : typedef.getAntChildren()) {
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

  private static URL toLocalURL(final File file) throws MalformedURLException {
    return new URL("file", "", FileUtil.toSystemIndependentName(file.getPath()));
  }


}
