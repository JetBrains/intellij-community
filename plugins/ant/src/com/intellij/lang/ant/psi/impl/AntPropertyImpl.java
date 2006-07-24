package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.psi.AntCall;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProperty;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class AntPropertyImpl extends AntTaskImpl implements AntProperty {

  private AntElement myPropHolder;
  private PsiElement myPropertiesFile;

  public AntPropertyImpl(final AntElement parent,
                         final XmlElement sourceElement,
                         final AntTypeDefinition definition,
                         @NonNls final String nameElementAttribute) {
    super(parent, sourceElement, definition, nameElementAttribute);
    myPropHolder = parent;
    if (myPropHolder instanceof AntCall) {
      myPropHolder = myPropHolder.getAntProject();
    }
    if (getName() != null) {
      myPropHolder.setProperty(getName(), this);
    }
    else if (getEnvironment() != null) {
      getAntProject().addEnvironmentPropertyPrefix(getEnvironment());
    }
  }

  public AntPropertyImpl(final AntElement parent, final XmlElement sourceElement, final AntTypeDefinition definition) {
    this(parent, sourceElement, definition, "name");
  }

  public String toString() {
    final @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntProperty[");
      if (getName() != null) {
        builder.append(getName());
        builder.append(" = ");
        builder.append(getValue());
      }
      else {
        final String propFile = getFileName();
        if (propFile != null) {
          builder.append("file: ");
          builder.append(propFile);
        }
        else {
          builder.append(getSourceElement().getName());
        }
      }
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public AntElementRole getRole() {
    return AntElementRole.PROPERTY_ROLE;
  }

  public String getFileReferenceAttribute() {
    return "file";
  }

  @Nullable
  public String getValue() {
    final XmlTag sourceElement = getSourceElement();
    if (sourceElement.getAttributeValue(getNameElementAttribute()) != null) {
      String value = sourceElement.getAttributeValue("value");
      if (value != null) {
        return value;
      }
      value = sourceElement.getAttributeValue("location");
      if (value != null) {
        String baseDir = getAntProject().getBaseDir();
        if (baseDir != null) {
          return new File(baseDir, value).getAbsolutePath();
        }
        return value;
      }
    }
    return null;
  }

  public void setValue(final String value) throws IncorrectOperationException {
    final XmlTag sourceElement = getSourceElement();
    if (sourceElement.getAttribute(getNameElementAttribute(), null) == null) {
      throw new IncorrectOperationException("Can't set value of an unnamed property");
    }
    sourceElement.setAttribute("value", value);
  }

  @Nullable
  public String getFileName() {
    return getSourceElement().getAttributeValue("file");
  }

  @Nullable
  public PropertiesFile getPropertiesFile() {
    if (myPropertiesFile == null) {
      myPropertiesFile = AntElementImpl.ourNull;
      final String name = getFileName();
      if (name != null) {
        final PsiFile psiFile = findFileByName(name);
        if (psiFile instanceof PropertiesFile) {
          myPropertiesFile = psiFile;
        }
      }
    }
    return (myPropertiesFile == AntElementImpl.ourNull) ? null : (PropertiesFile)myPropertiesFile;
  }

  public void setPropertiesFile(final String name) throws IncorrectOperationException {
    getSourceElement().setAttribute("file", name);
  }

  public String getPrefix() {
    return getSourceElement().getAttributeValue("prefix");
  }

  public String getEnvironment() {
    return getSourceElement().getAttributeValue("environment");
  }

  public void clearCaches() {
    super.clearCaches();
    myPropHolder.clearCaches();
    myPropertiesFile = null;
  }
}
