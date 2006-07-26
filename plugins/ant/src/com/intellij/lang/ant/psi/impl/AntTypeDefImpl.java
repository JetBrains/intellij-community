package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.AntTypeDef;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.lang.ant.psi.introspection.impl.AntTypeDefinitionImpl;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.StringBuilderSpinAllocator;
import org.apache.tools.ant.Task;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class AntTypeDefImpl extends AntTaskImpl implements AntTypeDef {
  private AntTypeDefinitionImpl myNewDefinition;

  public AntTypeDefImpl(final AntElement parent, final XmlElement sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
  }

  public void init() {
    super.init();
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
      getAntFile().clearCaches();
    }
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

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void loadClass(final String classname) {
    ClassLoader loader = getAntFile().getClassLoader().getClassloader();
    final String classpath = getClassPath();
    if (classpath != null) {
      try {
        final URL[] urls;
        if (classpath.indexOf(':') < 0) {
          urls = new URL[]{new URL("file://" + classpath)};
        }
        else {
          final List<URL> urlList = new ArrayList<URL>();
          for (String url : classpath.split(":")) {
            urlList.add(new URL("file://" + url));
          }
          urls = urlList.toArray(new URL[urlList.size()]);
        }
        loader = new URLClassLoader(urls, loader);
      }
      catch (MalformedURLException e) {
        // ignore
      }
    }
    Class clazz;
    try {
      if (loader == null) {
        clazz = Class.forName(classname);
      }
      else {
        clazz = loader.loadClass(classname);
      }
    }
    catch (Exception e) {
      clazz = null;
    }
    final String name = getDefinedName();
    final String uri = getUri();
    final AntTypeId id = (uri == null) ? new AntTypeId(name) : new AntTypeId(name, uri);
    if (clazz == null) {
      myNewDefinition = null;
    }
    else {
      myNewDefinition = (AntTypeDefinitionImpl)AntFileImpl.createTypeDefinition(id, clazz, Task.class.isAssignableFrom(clazz));
    }
    if (myNewDefinition != null) {
      myNewDefinition.setDefiningElement(this);
      final AntStructuredElement parent = getAntParent();
      if (parent != null) {
        parent.registerCustomType(myNewDefinition);
      }
    }
  }
}
