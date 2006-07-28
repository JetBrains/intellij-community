package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntAnt;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.impl.reference.AntTargetReference;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AntAntImpl extends AntTaskImpl implements AntAnt {

  @NonNls private static final String DEFAULT_ANTFILE = "build.xml";

  public AntAntImpl(final AntElement parent, final XmlElement sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
  }

  public String toString() {
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("Ant[");
      builder.append(getFileName());
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public String getFileReferenceAttribute() {
    return "antfile";
  }

  @NotNull
  public String getFileName() {
    final String result = getSourceElement().getAttributeValue(getFileReferenceAttribute());
    if (result == null) {
      return DEFAULT_ANTFILE;
    }
    return result;
  }

  @Nullable
  public String getTargetName() {
    return computeAttributeValue(getSourceElement().getAttributeValue("target"));
  }

  @NotNull
  public PsiReference[] getReferences() {
    final PsiReference[] result = super.getReferences();
    for (final PsiReference reference : result) {
      if (reference instanceof AntTargetReference) {
        ((AntTargetReference)reference).setShouldBeSkippedByAnnotator(findFileByName(getFileName()) == null);
      }
    }
    return result;
  }
}
