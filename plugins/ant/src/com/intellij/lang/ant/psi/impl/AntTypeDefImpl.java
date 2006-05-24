package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.AntTypeDef;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.StringBuilderSpinAllocator;
import org.apache.tools.ant.Task;
import org.jetbrains.annotations.NonNls;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

public class AntTypeDefImpl extends AntTaskImpl implements AntTypeDef {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.psi.impl.AntTypeDefImpl");
  private AntTypeDefinition myNewDefinition;

  public AntTypeDefImpl(final AntElement parent,
                        final XmlElement sourceElement,
                        final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
    final String classname = getClassName();
    if (classname == null) return;
    myNewDefinition = getAntFile().getBaseTypeDefinition(classname);
    if (myNewDefinition == null) {
      loadClass(classname);
    }
  }

  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntTypeDef[");
      builder.append(getSourceElement().getName());
      builder.append("]");
      if (myNewDefinition != null) {
        builder.append(" class=");
        builder.append(myNewDefinition.getClassName());
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

  public AntTypeDefinition getDefinition() {
    return myNewDefinition;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void loadClass(final String classname) {
    final String classpath = getClassPath();
    ClassLoader loader = null;
    if (classpath != null) {
      try {
        loader = new URLClassLoader(new URL[]{new URL("file://" + classpath)}, getClass().getClassLoader());
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
    catch (ClassNotFoundException e) {
      clazz = null;
    }
    final String name = getDefinedName();
    final String uri = getUri();
    AntTypeId id = (uri == null) ? new AntTypeId(name) : new AntTypeId(name, uri);
    if (clazz == null) {
      myNewDefinition = null;
    }
    else {
      myNewDefinition = AntFileImpl.createTypeDefinition(id, clazz, Task.class.isAssignableFrom(clazz));
      final AntStructuredElement se = PsiTreeUtil.getParentOfType(this, AntStructuredElementImpl.class);
      if (se != null) {
        se.registerCustomType(myNewDefinition);
      }
    }
  }
}
