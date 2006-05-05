package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntComment;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;

public class AntCommentImpl extends AntElementImpl implements AntComment {

  public AntCommentImpl(AntElement parent, XmlElement sourceElement) {
    super(parent, sourceElement);
  }

  public IElementType getTokenType() {
    return ((XmlComment) getSourceElement()).getTokenType();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "AntComment";
  }

  @NotNull
  public PsiReference[] getReferences() {
    return PsiReference.EMPTY_ARRAY;
  }
}
