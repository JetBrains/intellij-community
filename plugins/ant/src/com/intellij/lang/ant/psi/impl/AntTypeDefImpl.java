package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.impl.reference.AntRefIdReference;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.lang.ant.psi.introspection.impl.AntTypeDefinitionImpl;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
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
import com.intellij.util.lang.UrlClassLoader;
import org.apache.tools.ant.PathTokenizer;
import org.apache.tools.ant.Task;
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
import java.util.List;

public class AntTypeDefImpl extends AntTaskImpl implements AntTypeDef {
  @NonNls private static final String CLASSPATH_ATTR = "classpath";
  @NonNls private static final String URI_ATTR = "uri";
  @NonNls private static final String RESOURCE_ATTR = "resource";
  @NonNls private static final String FILE_ATTR = AntFileImpl.FILE_ATTR;
  @NonNls private static final String CLASSNAME_ATTR = "classname";
  @NonNls private static final String ADAPTER_ATTR = "adapter";
  @NonNls private static final String ADAPTTO_ATTR = "adaptto";

  private static final ClassCache CLASS_CACHE = new ClassCache();

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
      if (myNewDefinitions == null) {
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
        myClassesLoaded = false;
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
      final AntElement parent = element.getAntParent();
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
            if (parent instanceof AntStructuredElementImpl) {
              ((AntStructuredElementImpl)parent).registerCustomType(def);
            }
            else {
              final AntFile file = element.getAntFile();
              if (file != null) {
                file.registerCustomType(def);
              }
            }
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
        loadClass(antFile, property.getValue(), property.getKey(), getUri(),getAntParent());
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
    boolean newlyLoaded = false;
    final List<URL> urls = getClassPathUrls();
    Class clazz = CLASS_CACHE.getClass(urls, classname);
    if (clazz == null) {
      final ClassLoader loader = getClassLoader(urls);
      if (loader != null) {
        try {
          clazz = loader.loadClass(classname);
          newlyLoaded = true;
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
    }
    final String nsPrefix = (uri == null) ? null : getSourceElement().getPrefixByNamespace(uri);
    final AntTypeId id = (nsPrefix == null) ? new AntTypeId(name) : new AntTypeId(name, nsPrefix);
    AntTypeDefinitionImpl def = null;
    if (clazz == null) {
      def = new AntTypeDefinitionImpl(id, classname, isTask());
    }
    else {
      myClassesLoaded = true;
      final boolean isTask = isTask(clazz);
      def = (AntTypeDefinitionImpl)AntFileImpl.createTypeDefinition(id, clazz, isTask);
      if (def == null) { // can be null if failed to introspect the class for some reason
        def = new AntTypeDefinitionImpl(id, classname, isTask);
      }
      def.setIsProperty(isAssignableFrom(org.apache.tools.ant.taskdefs.Property.class.getName(), clazz));
      if (newlyLoaded) {
        CLASS_CACHE.setClass(urls, classname, clazz);
      }
    }
    if (def != null) {
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
    }
    return def;
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
    final String baseDir = computeAttributeValue("${" + AntFileImpl.BASEDIR_ATTR + "}");
    // check classpath attribute
    final String classpath = getClassPath();
    if (classpath != null) {
      final PathTokenizer tokenizer = new PathTokenizer(classpath);
      while (tokenizer.hasMoreTokens()) {
        addUrl(baseDir, urls, tokenizer.nextToken());
      }
    }
    
    final List<File> files = new ArrayList<File>();
    // check 'classpathref'
    for (PsiReference reference : getReferences()) {
      if (reference instanceof AntRefIdReference) {
        final PsiElement resolved = reference.resolve();
        if (resolved instanceof AntFilesProvider) {
          files.addAll(((AntFilesProvider)resolved).getFiles());
        }
      }
    }
    // check nested elements
    for (AntElement antElement : getChildren()) {
      if (antElement instanceof AntFilesProvider) {
        files.addAll(((AntFilesProvider)antElement).getFiles());
      }
    }
    
    for (File file : files) {
      try {
        urls.add(file.toURL());
      }
      catch (MalformedURLException e) {
      }
    }
    
    return urls;
  }

  private static void addUrl(final String baseDir, final List<URL> urls, final String path) {
    if (path != null) {
      try {
        File file = new File(path);
        if (file.isAbsolute()) {
          urls.add(file.toURL());
        }
        else {
          if (baseDir != null) {
            urls.add(new File(baseDir, path).toURL());
          }
        }
      }
      catch (MalformedURLException ignored) {
      }
    }
  }
  
  @Nullable
  private ClassLoader getClassLoader(final List<URL> urls) {
    ClassLoader loader = null;
    final AntFile file = getAntFile();
    if (file != null) {
      loader = file.getClassLoader();
      if (urls.size() > 0) {
        loader = new UrlClassLoader(urls, loader);
      }
    }
    return loader;
  }

  private boolean isTask() {
    return "taskdef".equals(getSourceElement().getName());
  }

  private static PsiFile createDummyFile(@NonNls final String name,
                                         final LanguageFileType type,
                                         final CharSequence str,
                                         final PsiManager manager) {
    return manager.getElementFactory().createFileFromText(name, type, str, LocalTimeCounter.currentTime(), false, false);
  }

  private static class ClassEntry {

    public static final URL[] EMPTY_URL_ARRAY = new URL[0];
    private final List<URL> myUrls;
    private final String myClassname;

    public ClassEntry(@NotNull final List<URL> urls, @NotNull final String classname) {
      myUrls = urls;
      myClassname = classname;
    }

    public final int hashCode() {
      return 31 * myClassname.hashCode() + myUrls.hashCode();
    }

    public final boolean equals(Object obj) {
      if (!(obj instanceof ClassEntry)) {
        return false;
      }
      final ClassEntry entry = (ClassEntry)obj;
      if (!myClassname.equals(entry.myClassname)) {
        return false;
      }
      if (!myUrls.equals(entry.myUrls)) {
        return false;
      }
      return true;
    }
  }

  private static class ClassCache extends ObjectCache<ClassEntry, SoftReference<Class>> {

    public ClassCache() {
      super(256);
    }

    @Nullable
    public final synchronized Class getClass(@NotNull final List<URL> urls, @NotNull final String classname) {
      final ClassEntry key = new ClassEntry(urls, classname);
      final SoftReference<Class> ref = tryKey(key);
      final Class result = (ref == null) ? null : ref.get();
      if (result == null && ref != null) {
        remove(key);
      }
      return result;
    }

    public final synchronized void setClass(@NotNull final List<URL> urls, @NotNull final String classname, @NotNull final Class clazz) {
      cacheObject(new ClassEntry(urls, classname), new SoftReference<Class>(clazz));
    }
  }
}
