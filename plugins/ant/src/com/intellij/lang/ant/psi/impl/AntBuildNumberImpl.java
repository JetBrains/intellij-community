package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProperty;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class AntBuildNumberImpl extends AntTaskImpl implements AntProperty {

  @NonNls private static final String BUILD_NUMBER_PROPERTY = "build.number";

  public AntBuildNumberImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
    final AntFile antFile = getAntFile();
    if (antFile != null) {
      antFile.setProperty(BUILD_NUMBER_PROPERTY, this);
    }
  }

  public String toString() {
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntProperty[");
      builder.append(getName());
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public String getName() {
    return BUILD_NUMBER_PROPERTY;
  }

  @NotNull
  public List<String> getFileReferenceAttributes() {
    return Collections.singletonList(AntFileImpl.FILE_ATTR);
  }

  @Nullable
  public String getValue(final String propName) {
    return null;
  }

  @Nullable
  public String getFileName() {
    return null;
  }

  @Nullable
  public PropertiesFile getPropertiesFile() {
    return null;
  }

  @Nullable
  public String getPrefix() {
    return null;
  }

  @Nullable
  public String getEnvironment() {
    return null;
  }

  @Nullable
  public String[] getNames() {
    return new String[]{getName()};
  }

  @Nullable
  public AntElement getFormatElement(final String propName) {
    return this;
  }
}
