package com.intellij.lang.properties.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.properties.PropertiesSupportLoader;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 30, 2005
 * Time: 8:23:10 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesElementImpl extends ASTWrapperPsiElement  {
  public PropertiesElementImpl(final ASTNode node) {
    super(node);
  }

  public Language getLanguage() {
    return PropertiesSupportLoader.FILE_TYPE.getLanguage();
  }

  //public void accept(PsiElementVisitor visitor) {
  //  if (visitor instanceof JSElementVisitor) {
  //    ((JSElementVisitor)visitor).visitJSElement(this);
  //  }
  //  else {
  //    visitor.visitElement(this);
  //  }
  //}
}
