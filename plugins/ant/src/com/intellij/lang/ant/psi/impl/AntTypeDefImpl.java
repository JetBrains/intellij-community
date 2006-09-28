package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.AntTypeDef;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.lang.ant.psi.introspection.impl.AntTypeDefinitionImpl;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.ObjectCache;
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
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class AntTypeDefImpl extends AntTaskImpl implements AntTypeDef {

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

  public String getFileReferenceAttribute() {
    return AntFileImpl.FILE_ATTR;
  }

  @Nullable
  public String getDefinedName() {
    return computeAttributeValue(getSourceElement().getAttributeValue(getNameElementAttribute()));
  }

  @Nullable
  public String getClassName() {
    return computeAttributeValue(getSourceElement().getAttributeValue("classname"));
  }

  @Nullable
  public String getClassPath() {
    return computeAttributeValue(getSourceElement().getAttributeValue("classpath"));
  }

  @Nullable
  public String getUri() {
    return computeAttributeValue(getSourceElement().getAttributeValue("uri"));
  }

  @Nullable
  public String getFile() {
    return computeAttributeValue(getSourceElement().getAttributeValue("file"));
  }

  @Nullable
  public String getResource() {
    return computeAttributeValue(getSourceElement().getAttributeValue("resource"));
  }

  @NonNls
  @Nullable
  public String getFormat() {
    return computeAttributeValue(getSourceElement().getAttributeValue("format"));
  }

  public void clearCaches() {
    super.clearCaches();
    if (myNewDefinitions != null) {
      final AntStructuredElement parent = getAntParent();
      if (parent != null) {
        for (final AntTypeDefinition def : myNewDefinitions) {
          parent.unregisterCustomType(def);
        }
      }
      myNewDefinitions = null;
      myClassesLoaded = false;
    }
    final AntFile file = getAntFile();
    if (file != null) {
      file.clearCaches();
    }
  }

  @NotNull
  public AntTypeDefinition[] getDefinitions() {
    if (myNewDefinitions == null) {
      myNewDefinitions = AntTypeDefinition.EMPTY_ARRAY;
      final String classname = getClassName();
      if (classname != null) {
        loadClass(classname, getDefinedName(), getUri());
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

  public boolean typesLoaded() {
    return myClassesLoaded;
  }

  @Nullable
  public String getLocalizedError() {
    return myLocalizedError;
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
      for (final Property property : propFile.getProperties()) {
        loadClass(property.getValue(), property.getKey(), getUri());
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
    final PsiFile psiFile = findFileByName(file);
    if (psiFile != null) {
      final VirtualFile vf = psiFile.getVirtualFile();
      if (vf != null) {
        try {
          final InputStream stream = vf.getInputStream();
          if (stream != null) {
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

  private void loadClass(@Nullable final String classname, @Nullable final String name, @Nullable final String uri) {

    if (classname == null || name == null || name.length() == 0) return;

    boolean newlyLoaded = false;
    final URL[] urls = getClassPathUrls();

    Class clazz = CLASS_CACHE.getClass(urls, classname);
    if (clazz == null) {
      ClassLoader loader = getClassLoader(urls);
      try {
        if (loader == null) {
          clazz = Class.forName(classname);
        }
        else {
          clazz = loader.loadClass(classname);
        }
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
    final String nsPrefix = (uri == null) ? null : getSourceElement().getPrefixByNamespace(uri);
    final AntTypeId id = (nsPrefix == null) ? new AntTypeId(name) : new AntTypeId(name, nsPrefix);
    final AntTypeDefinitionImpl def;
    if (clazz == null) {
      def = new AntTypeDefinitionImpl(id, classname, isTask());
    }
    else {
      myClassesLoaded = true;
      def = (AntTypeDefinitionImpl)AntFileImpl.createTypeDefinition(id, clazz, Task.class.isAssignableFrom(clazz));
    }
    if (def != null) {
      myNewDefinitions = new AntTypeDefinition[]{def};
      def.setDefiningElement(this);
      final AntStructuredElement parent = getAntParent();
      if (parent != null) {
        parent.registerCustomType(def);
      }
      if (newlyLoaded && clazz != null) {
        CLASS_CACHE.setClass(urls, classname, clazz);
      }
    }
  }

  private URL[] getClassPathUrls() {
    URL[] urls = ClassEntry.EMPTY_URL_ARRAY;
    final String classpath = getClassPath();
    if (classpath != null) {
      try {
        if (classpath.indexOf(File.pathSeparatorChar) < 0) {
          final File file = new File(computeAttributeValue(classpath));
          urls = new URL[]{file.toURL()};
        }
        else {
          final List<URL> urlList = new ArrayList<URL>();
          for (final String path : classpath.split(File.pathSeparator)) {
            final File file = new File(computeAttributeValue(path));
            urlList.add(file.toURL());
          }
          urls = urlList.toArray(new URL[urlList.size()]);
        }
      }
      catch (MalformedURLException e) {
        urls = ClassEntry.EMPTY_URL_ARRAY;
      }
    }
    return urls;
  }

  @Nullable
  private ClassLoader getClassLoader(final URL[] urls) {
    ClassLoader loader = null;
    final AntFile file = getAntFile();
    if (file != null) {
      loader = file.getClassLoader().getClassloader();
      if (urls.length > 0) {
        loader = new URLClassLoader(urls, loader);
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
    private final URL[] myUrls;
    private final String myClassname;

    public ClassEntry(@NotNull final URL[] urls, @NotNull final String classname) {
      myUrls = urls;
      myClassname = classname;
    }

    public final int hashCode() {
      int hc = myClassname.hashCode();
      for (final URL url : myUrls) {
        hc += url.hashCode();
      }
      return hc;
    }

    public final boolean equals(Object obj) {
      if (!(obj instanceof ClassEntry)) return false;
      final ClassEntry entry = (ClassEntry)obj;
      if (!myClassname.equals(entry.myClassname)) return false;
      if (myUrls.length != entry.myUrls.length) return false;
      for (final URL url : myUrls) {
        boolean found = false;
        for (final URL entryUrl : entry.myUrls) {
          if (url.equals(entryUrl)) {
            found = true;
            break;
          }
        }
        if (!found) return false;
      }
      return true;
    }
  }

  private static class ClassCache extends ObjectCache<ClassEntry, SoftReference<Class>> {

    public ClassCache() {
      super(256);
    }

    @Nullable
    public final synchronized Class getClass(@NotNull final URL[] urls, @NotNull final String classname) {
      final ClassEntry key = new ClassEntry(urls, classname);
      final SoftReference<Class> ref = tryKey(key);
      final Class result = (ref == null) ? null : ref.get();
      if (result == null && ref != null) {
        remove(key);
      }
      return result;
    }

    public final synchronized void setClass(@NotNull final URL[] urls, @NotNull final String classname, @NotNull final Class clazz) {
      cacheObject(new ClassEntry(urls, classname), new SoftReference<Class>(clazz));
    }
  }
}
