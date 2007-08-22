package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntElementVisitor;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AntTaskImpl extends AntStructuredElementImpl implements AntTask {

  public AntTaskImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
  }

  public AntTaskImpl(final AntElement parent,
                     final XmlTag sourceElement,
                     final AntTypeDefinition definition,
                     @NonNls final String nameElementAttribute) {
    super(parent, sourceElement, definition, nameElementAttribute);
  }

  public void acceptAntElementVisitor(@NotNull final AntElementVisitor visitor) {
    visitor.visitAntTask(this);
  }

  public String toString() {
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
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

  public AntElementRole getRole() {
    return (!isMacroDefined() && !isPresetDefined() && !isTypeDefined()) ? AntElementRole.TASK_ROLE : AntElementRole.USER_TASK_ROLE;
  }

  @Nullable
  public AntStructuredElement getAntParent() {
    return (AntStructuredElement)super.getAntParent();
  }

  public boolean isMacroDefined() {
    final AntTypeDefinition def = getTypeDefinition();
    return def != null && def.getClassName().startsWith(AntMacroDefImpl.ANT_MACRODEF_NAME);
  }
}
