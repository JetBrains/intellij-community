package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntTypeDef;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.lang.ant.psi.introspection.impl.AntTypeDefinitionImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.StringBuilderSpinAllocator;
import org.apache.tools.ant.Task;
import org.jetbrains.annotations.NonNls;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class AntTypeDefImpl extends AntTaskImpl implements AntTypeDef {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.psi.impl.AntTypeDefImpl");
  private AntTypeDefinitionImpl myNewDefinition;

  public AntTypeDefImpl(final AntElement parent, final XmlElement sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
    getDefinition();
  }

  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
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

  public String getDefinedName() {
    return getSourceElement().getAttributeValue(getNameElementAttribute());
  }

  public String getClassName() {
    return getSourceElement().getAttributeValue("classname");
  }

  public String getClassPath() {
    return getSourceElement().getAttributeValue("classpath");
  }

  public String getClassPathRef() {
    return getSourceElement().getAttributeValue("classpathref");
  }

  public String getLoaderRef() {
    return getSourceElement().getAttributeValue("loaderref");
  }

  public String getFormat() {
    return getSourceElement().getAttributeValue("format");
  }

  public String getUri() {
    return getSourceElement().getAttributeValue("uri");
  }

  public void clearCaches() {
    super.clearCaches();
    if (myNewDefinition != null) {
      getAntParent().unregisterCustomType(myNewDefinition);
      myNewDefinition = null;
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
    final String classpath = getClassPath();
    ClassLoader loader = null;
    if (classpath != null) {
      try {
        URL[] urls;
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
        loader = new URLClassLoader(urls, getClass().getClassLoader());
      }
      catch (MalformedURLException e) {
        LOG.error(e);
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
    AntTypeId id = (uri == null) ? new AntTypeId(name) : new AntTypeId(name, uri);
    if (clazz == null) {
      myNewDefinition = null;
    }
    else {
      myNewDefinition = (AntTypeDefinitionImpl)AntFileImpl.createTypeDefinition(id, clazz, Task.class.isAssignableFrom(clazz));
      myNewDefinition.setDefiningElement(this);
      getAntParent().registerCustomType(myNewDefinition);
    }
  }
}
