/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.config.impl.AntResourcesClassLoader;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.impl.reference.AntRefIdReference;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.lang.ant.psi.introspection.impl.AntTypeDefinitionImpl;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.ObjectCache;
import org.apache.tools.ant.PathTokenizer;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.TaskContainer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public class AntTypeDefImpl extends AntTaskImpl implements AntTypeDef {
  @NonNls private static final String CLASSPATH_ATTR = "classpath";
  @NonNls private static final String URI_ATTR = "uri";
  @NonNls private static final String RESOURCE_ATTR = "resource";
  @NonNls private static final String FILE_ATTR = AntFileImpl.FILE_ATTR;
  @NonNls private static final String CLASSNAME_ATTR = "classname";
  @NonNls private static final String ADAPTER_ATTR = "adapter";
  @NonNls private static final String ADAPTTO_ATTR = "adaptto";
  @NonNls private static final String BASEDIR_ANT_REFERENCE = "${" + AntFileImpl.BASEDIR_ATTR + "}";

  private static final ClassLoaderCache LOADERS_CACHE = new ClassLoaderCache();

  private AntTypeDefinition[] myNewDefinitions;
  private boolean myClassesLoaded;
  private String myLocalizedError;

  public AntTypeDefImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
    getDefinitions();
  }

  public String toString() {
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntTypeDef[");
      builder.append(getSourceElement().getName());
      builder.append("]");
      final AntTypeDefinition[] defs = getDefinitions();
      if (defs.length != 0) {
        builder.append(" classes={");
        builder.append(defs[0].getClassName());
        for (int i = 1; i < defs.length; ++i) {
          builder.append(',');
          builder.append(defs[i].getClassName());
        }
        builder.append("}");
      }
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @NotNull
  public List<String> getFileReferenceAttributes() {
    final List<String> attribs = super.getFileReferenceAttributes();
    
    final String cp = getClassPath();
    if (cp != null && cp.length() > 0 && !cp.contains(";") && !cp.contains(":")) {
      // only single-entry classpath is accepted
      final List<String> _attribs = new ArrayList<String>(attribs.size() + 1);
      _attribs.addAll(attribs);
      _attribs.add(CLASSPATH_ATTR);
      return _attribs;
    }
    
    return attribs;
  }

  @Nullable
  public String getDefinedName() {
    return computeAttributeValue(getSourceElement().getAttributeValue(getNameElementAttribute()));
  }

  @Nullable
  public String getClassName() {
    return computeAttributeValue(getSourceElement().getAttributeValue(CLASSNAME_ATTR));
  }

  @Nullable
  public String getAdapterName() {
    return computeAttributeValue(getSourceElement().getAttributeValue(ADAPTER_ATTR));
  }

  @Nullable
  public String getAdaptToName() {
    return computeAttributeValue(getSourceElement().getAttributeValue(ADAPTTO_ATTR));
  }

  @Nullable
  public String getClassPath() {
    return computeAttributeValue(getSourceElement().getAttributeValue(CLASSPATH_ATTR));
  }

  @Nullable
  public String getUri() {
    return computeAttributeValue(getSourceElement().getAttributeValue(URI_ATTR));
  }

  @Nullable
  public String getFile() {
    return computeAttributeValue(getSourceElement().getAttributeValue(FILE_ATTR));
  }

  @Nullable
  public String getResource() {
    return computeAttributeValue(getSourceElement().getAttributeValue(RESOURCE_ATTR));
  }

  @NonNls
  @Nullable
  public String getFormat() {
    return computeAttributeValue(getSourceElement().getAttributeValue("format"));
  }

  public void clearCaches() {
    synchronized (PsiLock.LOCK) {
      super.clearCaches();
      clearClassesCache();
      final AntFile file = getAntFile();
      if (file != null) {
        file.clearCaches();
      }
    }
  }

  @NotNull
  public AntTypeDefinition[] getDefinitions() {
    synchronized (PsiLock.LOCK) {
      if (myNewDefinitions == null || (!typesLoaded() && myLocalizedError == null /*don't give up loading until the error occurres or types are loaded*/)) {
        myNewDefinitions = AntTypeDefinition.EMPTY_ARRAY;
        final String classname = getClassName();
        if (classname != null) {
          final AntStructuredElement parent = getAntParent();
          final AntFile antFile = parent != null? parent.getAntFile() : null;
          loadClass(antFile, classname, getDefinedName(), getUri(),getAntParent());
        }
        else {
          final String resource = getResource();
          if (resource != null) {
            loadResource(resource);
          }
          else {
            final String file = getFile();
            if (file != null) {
              loadFile(file);
            }
          }
        }
      }
      return myNewDefinitions;
    }
  }

  public boolean typesLoaded() {
    return myClassesLoaded;
  }

  public void clearClassesCache() {
    synchronized(PsiLock.LOCK) {
      if (myNewDefinitions != null) {
        final AntStructuredElement parent = getAntParent();
        if (parent != null) {
          final AntProject antProject = !(parent instanceof AntProject)? parent.getAntProject() : null;
          for (final AntTypeDefinition def : myNewDefinitions) {
            parent.unregisterCustomType(def);
            if (antProject != null) {
              antProject.unregisterCustomType(def);
            }
          }
        }
        myNewDefinitions = null;
        //myClassesLoaded = false;
      }
    }
  }

  @Nullable
  public String getLocalizedError() {
    return myLocalizedError;
  }

  public void acceptAntElementVisitor(@NotNull final AntElementVisitor visitor) {
    visitor.visitAntTypedef(this);
  }

  static void loadAntlibStream(@NotNull final InputStream antlibStream, final AntStructuredElement element, final String ns) {
    final String nsPrefix = element.getSourceElement().getPrefixByNamespace(ns);
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      int nextByte;
      while ((nextByte = antlibStream.read()) >= 0) {
        builder.append((char)nextByte);
      }
      antlibStream.close();
      final XmlFile xmlFile = (XmlFile)createDummyFile("dummy.xml", StdFileTypes.XML, builder, element.getManager());
      final XmlDocument document = xmlFile.getDocument();
      if (document == null) return;
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
              ((AntTypeDefinitionImpl)def).setDefiningElement(td);
            }
          }
        }
      }
    }
    catch (IOException e) {
      if (element instanceof AntTypeDefImpl) {
        final AntTypeDefImpl td = ((AntTypeDefImpl)element);
        td.myClassesLoaded = false;
        td.myLocalizedError = e.getLocalizedMessage();
      }
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  private void loadPropertiesStream(final InputStream propStream, final AntStructuredElement element) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      int nextByte;
      while ((nextByte = propStream.read()) >= 0) {
        builder.append((char)nextByte);
      }
      propStream.close();
      final PropertiesFile propFile =
        (PropertiesFile)createDummyFile("dummy.properties", StdFileTypes.PROPERTIES, builder, element.getManager());
      final AntStructuredElement parent = getAntParent();
      final AntFile antFile = parent != null? parent.getAntFile() : null;
      for (final Property property : propFile.getProperties()) {
        loadClass(antFile, property.getValue(), property.getUnescapedKey(), getUri(),getAntParent());
      }
    }
    catch (IOException e) {
      if (element instanceof AntTypeDefImpl) {
        final AntTypeDefImpl td = ((AntTypeDefImpl)element);
        td.myClassesLoaded = false;
        td.myLocalizedError = e.getLocalizedMessage();
      }
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  private boolean isXmlFormat(@NonNls @NotNull final String resourceOfFileName) {
    @NonNls final String format = getFormat();
    if (format != null) {
      return format.equals("xml");
    }
    return resourceOfFileName.endsWith(".xml");
  }

  private void loadResource(@NotNull final String resource) {
    ClassLoader loader = getClassLoader(getClassPathUrls());
    if (loader != null) {
      final InputStream stream = loader.getResourceAsStream(resource);
      if (stream != null) {
        myClassesLoaded = true;
        if (!isXmlFormat(resource)) {
          loadPropertiesStream(stream, this);
        }
        else {
          loadAntlibStream(stream, this, getUri());
        }
      }
    }
  }

  private void loadFile(final String file) {
    final PsiFile psiFile = findFileByName(file, null);
    if (psiFile != null) {
      final VirtualFile vf = psiFile.getVirtualFile();
      if (vf != null) {
        try {
          final InputStream stream = vf.getInputStream();
          if (stream != null) {
            myClassesLoaded = true;
            if (!isXmlFormat(file)) {
              loadPropertiesStream(stream, this);
            }
            else {
              loadAntlibStream(stream, this, getUri());
            }
          }
        }
        catch (IOException e) {
          //ignore
        }
      }
    }
  }

  private AntTypeDefinitionImpl loadClass(final @Nullable AntFile antFile,
                                          @Nullable final String classname,
                                          @Nullable final String name,
                                          @Nullable final String uri,
                                          final AntStructuredElement parent) {
    if (classname == null || name == null || name.length() == 0) {
      return null;
    }
    ProgressManager.checkCanceled();
    final List<URL> urls = getClassPathUrls();
    Class clazz = null;
    final ClassLoader loader = getClassLoader(urls);
    if (loader != null) {
      try {
        clazz = loader.loadClass(classname);
      }
      catch (ClassNotFoundException e) {
        myLocalizedError = e.getLocalizedMessage();
        clazz = null;
      }
      catch (NoClassDefFoundError e) {
        myLocalizedError = e.getLocalizedMessage();
        clazz = null;
      }
      catch (UnsupportedClassVersionError e) {
        myLocalizedError = e.getLocalizedMessage();
        clazz = null;
      }
    }
    final String nsPrefix = (uri == null) ? null : getSourceElement().getPrefixByNamespace(uri);
    final AntTypeId id = (nsPrefix == null) ? new AntTypeId(name) : new AntTypeId(name, nsPrefix);
    AntTypeDefinitionImpl def;
    if (clazz == null) {
      def = new AntTypeDefinitionImpl(id, classname, isTask(), false);
    }
    else {
      myClassesLoaded = true;
      final boolean isTask = isTask(clazz);
      def = (AntTypeDefinitionImpl)AntFileImpl.createTypeDefinition(id, clazz, isTask);
      if (def == null) { // can be null if failed to introspect the class for some reason
        def = new AntTypeDefinitionImpl(id, classname, isTask, isAssignableFrom(TaskContainer.class.getName(), clazz));
      }
      else {
        fixNestedDefinitions(def);
      }
      def.setIsProperty(isAssignableFrom(org.apache.tools.ant.taskdefs.Property.class.getName(), clazz));
    }
    myNewDefinitions = ArrayUtil.append(myNewDefinitions, def);
    def.setDefiningElement(this);
    if (parent != null) {
      if (!(parent instanceof AntProject)) {
        // make custom definition available at project level first
        final AntProject antProject = parent.getAntProject();
        if (antProject != null) {
          antProject.registerCustomType(def);
        }
      }
      parent.registerCustomType(def);
    }
    else {
      if (antFile != null) {
        antFile.registerCustomType(def);
      }
    }
    if (antFile != null) {
      for (final AntTypeId typeId : def.getNestedElements()) {
        final String nestedClassName = def.getNestedClassName(typeId);
        AntTypeDefinitionImpl nestedDef = (AntTypeDefinitionImpl)antFile.getBaseTypeDefinition(nestedClassName);
        if (nestedDef == null) {
          nestedDef = loadClass(antFile, nestedClassName, typeId.getName(), uri, null);
          if (nestedDef != null) {
            def.registerNestedType(nestedDef.getTypeId(), nestedDef.getClassName());
          }
        }
      }
    }
    return def;
  }

  private void fixNestedDefinitions(final AntTypeDefinitionImpl def) {
    // hack to overcome problems with poorly written custom tasks. 
    // For example: net.sf.antcontrib.logic.ForTask.createSequential() is defined to 
    // return java.langObject instead of org.apache.tools.ant.taskdefs.Sequential 
    
    for (AntTypeId nestedTypeId : def.getNestedElements()) {
      final String className = def.getNestedClassName(nestedTypeId);
      if ("java.lang.Object".equals(className)) {
        // check if such typeId is already mapped correct the definition's nested element
        for (AntTypeDefinition typeDefinition : getAntFile().getBaseTypeDefinitions()) {
          if (nestedTypeId.equals(typeDefinition.getTypeId()) && !"java.lang.Object".equals(typeDefinition.getClassName())) {
            def.registerNestedType(nestedTypeId, typeDefinition.getClassName());
            break;
          }
        }
      }
    }
  }

  private boolean isTask(final Class clazz) {
    if (isTask()) { // in taskdef, the adapter is always set to Task
      return true;
    }
    
    final String adaptto = getAdaptToName();
    if (adaptto != null && isAssignableFrom(adaptto, clazz)) {
      return isAssignableFrom(Task.class.getName(), clazz);
    }
    
    final String adapter = getAdapterName();
    if (adapter != null) {
      try {
        final Class adapterClass = clazz.getClassLoader().loadClass(adapter);
        return isAssignableFrom(Task.class.getName(), adapterClass);
      }
      catch (ClassNotFoundException ignored) {
      }
      catch (NoClassDefFoundError ignored) {
      }
      catch (UnsupportedClassVersionError ignored) {
      }
    }
    
    return isAssignableFrom(Task.class.getName(), clazz);
  }

  private static boolean isAssignableFrom(final String baseClassName, final Class clazz) {
    try {
      final ClassLoader loader = clazz.getClassLoader();
      if (loader != null) {
        final Class baseClass = loader.loadClass(baseClassName);
        return baseClass.isAssignableFrom(clazz);
      }
    }
    catch (ClassNotFoundException ignored) {
    }
    return false;
  }

  private List<URL> getClassPathUrls() {
    final List<URL> urls = getClassPathLocalUrls();
    AntElement parent = getAntParent();
    while (parent != null && !(parent instanceof AntProject)) {
      if (parent instanceof AntTypeDefImpl) {
        urls.addAll(((AntTypeDefImpl)parent).getClassPathLocalUrls());
      }
      parent = parent.getAntParent();
    }
    return urls;
  }

  private List<URL> getClassPathLocalUrls() {
    final List<URL> urls = new ArrayList<URL>();
    final String baseDir = computeAttributeValue(BASEDIR_ANT_REFERENCE);
    // check classpath attribute
    final String classpath = getClassPath();
    if (classpath != null) {
      final PathTokenizer tokenizer = new PathTokenizer(classpath);
      while (tokenizer.hasMoreTokens()) {
        addUrl(baseDir, urls, tokenizer.nextToken());
      }
    }
    
    final List<File> files = new ArrayList<File>();
    final HashSet<AntFilesProvider> processed = new HashSet<AntFilesProvider>(); // aux collection 
    // check 'classpathref'
    for (PsiReference reference : getReferences()) {
      ProgressManager.checkCanceled();
      if (reference instanceof AntRefIdReference) {
        final PsiElement resolved = reference.resolve();
        if (resolved instanceof AntFilesProvider) {
          files.addAll(((AntFilesProvider)resolved).getFiles(processed));
        }
      }
    }
    // check nested elements
    for (AntElement antElement : getChildren()) {
      ProgressManager.checkCanceled();
      if (antElement instanceof AntFilesProvider) {
        files.addAll(((AntFilesProvider)antElement).getFiles(processed));
      }
    }
    
    for (File file : files) {
      try {
        //urls.add(file.toURL());
        // toURL() implementation invokes File.isDirectory() which, if called too often, may cause performance problems
        urls.add(toLocalURL(file));
      }
      catch (MalformedURLException e) {
      }
    }
    
    return urls;
  }

  private static URL toLocalURL(final File file) throws MalformedURLException {
    return new URL("file", "", FileUtil.toSystemIndependentName(file.getPath()));
  }

  private static void addUrl(final String baseDir, final List<URL> urls, final String path) {
    if (path != null) {
      try {
        File file = new File(path);
        if (file.isAbsolute()) {
          urls.add(toLocalURL(file));
        }
        else {
          if (baseDir != null) {
            urls.add(toLocalURL(new File(baseDir, path)));
          }
        }
      }
      catch (MalformedURLException ignored) {
      }
    }
  }
  
  @Nullable
  private ClassLoader getClassLoader(final List<URL> urls) {
    final AntFile file = getAntFile();
    final ClassLoader parentLoader = file != null ? file.getClassLoader() : null;
    if (urls.size() == 0) {
      return parentLoader;
    }
    
    final ClassLoader cached = LOADERS_CACHE.getClassLoader(urls);
    if (cached != null && parentLoader == cached.getParent()) {
      return cached;
    }
    
    final AntResourcesClassLoader loader = new AntResourcesClassLoader(urls, parentLoader, false, false);
    LOADERS_CACHE.setClassLoader(urls, loader);
    return loader;
  }

  private boolean isTask() {
    return "taskdef".equals(getSourceElement().getName());
  }

  private static PsiFile createDummyFile(@NonNls final String name,
                                         final LanguageFileType type,
                                         final CharSequence str,
                                         final PsiManager manager) {
    return PsiFileFactory.getInstance(manager.getProject())
      .createFileFromText(name, type, str, LocalTimeCounter.currentTime(), false, false);
  }

  private static class CacheKey {

    public static final URL[] EMPTY_URL_ARRAY = new URL[0];
    private final List<URL> myUrls;
    private final int myHashCode;

    public CacheKey(@NotNull final List<URL> urls) {
      myUrls = urls;
      int hashCode = 1;
      for (URL url : urls) {
        hashCode = 31 * hashCode + url.getPath().hashCode();
      }
      myHashCode = hashCode;
    }

    public final int hashCode() {
      return myHashCode;
    }

    public final boolean equals(Object obj) {
      if (!(obj instanceof CacheKey)) {
        return false;
      }
      final CacheKey entry = (CacheKey)obj;
      final List<URL> urls = myUrls;
      final List<URL> thatUrls = entry.myUrls;
      if (urls.size() != thatUrls.size()) {
        return false;
      }
      final Iterator<URL> urlsIt = urls.iterator();
      final Iterator<URL> thatUrlsIt = thatUrls.iterator();
      while (urlsIt.hasNext()) {
        if (!FileUtil.pathsEqual(urlsIt.next().getPath(), thatUrlsIt.next().getPath())) {
          return false;
        }
      }
      return true;
    }
  }

  private static class ClassLoaderCache extends ObjectCache<CacheKey, SoftReference<ClassLoader>> {

    public ClassLoaderCache() {
      super(256);
    }

    @Nullable
    public final synchronized ClassLoader getClassLoader(@NotNull final List<URL> urls) {
      final CacheKey key = new CacheKey(urls);
      final SoftReference<ClassLoader> ref = tryKey(key);
      final ClassLoader result = (ref == null) ? null : ref.get();
      if (result == null && ref != null) {
        remove(key);
      }
      return result;
    }

    public final synchronized void setClassLoader(@NotNull final List<URL> urls, @NotNull final ClassLoader loader) {
      cacheObject(new CacheKey(urls), new SoftReference<ClassLoader>(loader));
    }
  }
}
