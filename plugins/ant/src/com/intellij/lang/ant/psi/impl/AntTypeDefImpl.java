package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.AntTypeDef;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.lang.ant.psi.introspection.impl.AntTypeDefinitionImpl;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.ObjectCache;
import org.apache.tools.ant.Task;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class AntTypeDefImpl extends AntTaskImpl implements AntTypeDef {

  private static final ClassCache CLASS_CACHE = new ClassCache();
  private AntTypeDefinitionImpl myNewDefinition;
  private boolean myClassesLoaded;

  public AntTypeDefImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
    getDefinition();
  }

  public String toString() {
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntTypeDef[");
      builder.append(getSourceElement().getName());
      builder.append("]");
      if (getDefinition() != null) {
        builder.append(" class=");
        builder.append(getDefinition().getClassName());
      }
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
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
  public String getClassPathRef() {
    return computeAttributeValue(getSourceElement().getAttributeValue("classpathref"));
  }

  @Nullable
  public String getLoaderRef() {
    return computeAttributeValue(getSourceElement().getAttributeValue("loaderref"));
  }

  @Nullable
  public String getFormat() {
    return computeAttributeValue(getSourceElement().getAttributeValue("format"));
  }

  @Nullable
  public String getUri() {
    return computeAttributeValue(getSourceElement().getAttributeValue("uri"));
  }

  public void clearCaches() {
    super.clearCaches();
    if (myNewDefinition != null) {
      final AntStructuredElement parent = getAntParent();
      if (parent != null) {
        parent.unregisterCustomType(myNewDefinition);
      }
      myNewDefinition = null;
      myClassesLoaded = false;
    }
    getAntFile().clearCaches();
  }

  public AntTypeDefinition getDefinition() {
    if (myNewDefinition == null) {
      final String classname = getClassName();
      if (classname != null) {
        loadClass(classname);
      }
    }
    return myNewDefinition;
  }

  public boolean typesLoaded() {
    return myClassesLoaded;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void loadClass(final String classname) {
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

    boolean newlyLoaded = false;
    Class clazz = CLASS_CACHE.getClass(urls, classname);
    if (clazz == null) {
      ClassLoader loader = getAntFile().getClassLoader().getClassloader();
      if (urls.length > 0) {
        loader = new URLClassLoader(urls, loader);
      }
      try {
        if (loader == null) {
          clazz = Class.forName(classname);
        }
        else {
          clazz = loader.loadClass(classname);
        }
        newlyLoaded = true;
      }
      catch (Exception e) {
        clazz = null;
      }
    }
    final String name = getDefinedName();
    final String uri = getUri();
    final String nsPrefix = (uri == null) ? null : getSourceElement().getPrefixByNamespace(uri);
    final AntTypeId id = (nsPrefix == null) ? new AntTypeId(name) : new AntTypeId(name, nsPrefix);
    if (clazz == null) {
      myNewDefinition = new AntTypeDefinitionImpl(id, classname, isTask());
    }
    else {
      myClassesLoaded = true;
      myNewDefinition = (AntTypeDefinitionImpl)AntFileImpl.createTypeDefinition(id, clazz, Task.class.isAssignableFrom(clazz));
    }
    if (myNewDefinition != null) {
      myNewDefinition.setDefiningElement(this);
      final AntStructuredElement parent = getAntParent();
      if (parent != null) {
        parent.registerCustomType(myNewDefinition);
      }
      if (newlyLoaded && clazz != null) {
        CLASS_CACHE.setClass(urls, classname, clazz);
      }
    }
  }

  private boolean isTask() {
    return "taskdef".equals(getSourceElement().getName());
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
