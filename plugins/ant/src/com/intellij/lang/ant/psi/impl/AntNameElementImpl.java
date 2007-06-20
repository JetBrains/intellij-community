package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AntNameElementImpl extends AntElementImpl {

  private @Nullable AntElement myMirrorElement;
  private String myCachedName;
  private volatile long myModCount;

  public AntNameElementImpl(final AntElement parent, final XmlAttributeValue sourceElement) {
    super(parent, sourceElement);
  }

  @NotNull
  public XmlAttributeValue getSourceElement() {
    return (XmlAttributeValue)super.getSourceElement();
  }

  public String getName() {
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
  
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    final XmlAttribute attr = PsiTreeUtil.getParentOfType(getSourceElement(), XmlAttribute.class);
    if (attr != null) {
      attr.setValue(name);
    }
    return this;
  }

  public final void setMirrorElement(@Nullable final AntElement renameElement) {
    myMirrorElement = renameElement;
  }

  @NotNull
  public final AntElement getMirrorElement() {
    return myMirrorElement != null? myMirrorElement : this;
  }
}
