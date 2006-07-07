package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class AntTaskImpl extends AntStructuredElementImpl implements AntTask {

  public AntTaskImpl(final AntElement parent, final XmlElement sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
  }

  public AntTaskImpl(final AntElement parent,
                     final XmlElement sourceElement,
                     final AntTypeDefinition definition,
                     @NonNls final String nameElementAttribute) {
    super(parent, sourceElement, definition, nameElementAttribute);
  }

  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntTask[");
      builder.append(getSourceElement().getName());
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @Nullable
  public AntStructuredElement getAntParent() {
    return (AntStructuredElement)super.getAntParent();
  }

  public boolean isMacroDefined() {
    return myDefinition != null && myDefinition.getClassName().startsWith(AntMacroDefImpl.ANT_MACRODEF_NAME);
  }
}
