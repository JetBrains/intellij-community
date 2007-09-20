package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntNameIdentifier;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class AntNameIdentifierImpl extends AntElementImpl implements AntNameIdentifier {

  private String myCachedName;
  private volatile long myModCount;

  public AntNameIdentifierImpl(final AntElement parent, final XmlAttributeValue sourceElement) {
    super(parent, sourceElement);
  }

  @NotNull
  public XmlAttributeValue getSourceElement() {
    return (XmlAttributeValue)super.getSourceElement();
  }

  public TextRange getTextRange() {
    return getSourceElement().getValueTextRange();
  }

  public String getText() {
    return getName();
  }

  public int getTextOffset() {
    return getTextRange().getStartOffset();
  }

  public int getTextLength() {
    return getText().length();
  }

  public String getName() {
    return getIdentifierName();
  }

  public String getIdentifierName() {
    String name = myCachedName;
    final PsiManager psiManager = getManager();
    final long modificationCount = psiManager != null? psiManager.getModificationTracker().getModificationCount() : myModCount;
    if (name != null && myModCount == modificationCount) {
      return name;
    }
    myModCount = modificationCount;
    name = getSourceElement().getValue();
    myCachedName = name;
    return name;
  }

  public void setIdentifierName(@NotNull String name) throws IncorrectOperationException {
    final XmlAttribute attr = PsiTreeUtil.getParentOfType(getSourceElement(), XmlAttribute.class, true, true);
    if (attr != null) {
      attr.setValue(name);
    }
  }
}
