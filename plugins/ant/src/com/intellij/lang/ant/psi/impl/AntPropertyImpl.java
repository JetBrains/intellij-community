package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntCall;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProperty;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class AntPropertyImpl extends AntTaskImpl implements AntProperty {

  public AntPropertyImpl(final AntElement parent, final XmlElement sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
    AntElement propHolder = parent;
    if (propHolder instanceof AntCall) {
      propHolder = propHolder.getAntProject();
    }
    if (getName() != null) {
      propHolder.setProperty(getName(), this);
    } else if (getFileName() != null) {
      final PropertiesFile file = getPropertiesFile();
      if (file != null) {
        for (Property prop : file.getProperties()) {
          propHolder.setProperty(prop.getKey(), prop);
        }
      }
    }
  }

  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntProperty: [");
      if (getName() != null) {
        builder.append(getName());
        builder.append(" = ");
        builder.append(getValue());
      } else {
        final String propFile = getFileName();
        if (propFile != null) {
          builder.append("file: ");
          builder.append(propFile);
        } else {
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

  @Nullable
  public String getValue() {
    final XmlTag sourceElement = getSourceElement();
    if (sourceElement.getAttributeValue("name") != null) {
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
    if (sourceElement.getAttribute("name", null) == null) {
      throw new IncorrectOperationException("Can't set value of an unnamed property");
    }
    sourceElement.setAttribute("value", value);
    subtreeChanged();
  }

  @Nullable
  public String getFileName() {
    return getSourceElement().getAttributeValue("file");
  }

  @Nullable
  public PropertiesFile getPropertiesFile() {
    return (PropertiesFile) findFileByName(getFileName());
  }

  public void setPropertiesFile(final String name) throws IncorrectOperationException {
    getSourceElement().setAttribute("file", name);
    subtreeChanged();
  }
}
