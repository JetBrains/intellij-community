// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n.folding;

import com.intellij.lang.properties.IProperty;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.expressions.UInjectionHost;

import java.util.Iterator;
import java.util.List;

public class PropertyFoldingEditHandler {
  private final UCallExpression myCallExpression;
  private final IProperty myProperty;

  public PropertyFoldingEditHandler(PsiElement foldedPsiElement) {
    UInjectionHost injectionHost = null;
    if (foldedPsiElement != null && foldedPsiElement.isValid()) {
      myCallExpression = findCallExpression(foldedPsiElement);
      if (myCallExpression == null) {
        injectionHost = UastContextKt.toUElementOfExpectedTypes(foldedPsiElement, UInjectionHost.class);
      }
      else {
        injectionHost = ObjectUtils.tryCast(myCallExpression.getArgumentForParameter(0), UInjectionHost.class);
      }
    }
    else {
      myCallExpression = null;
    }
    myProperty = injectionHost == null ? null : PropertyFoldingBuilder.getI18nProperty(injectionHost);
  }

  private static UCallExpression findCallExpression(PsiElement foldedPsiElement) {
    UCallExpression expression = UastContextKt.toUElement(foldedPsiElement, UCallExpression.class);
    if (expression != null) return expression;
    for (PsiElement child = foldedPsiElement.getFirstChild(); child != null; child = child.getNextSibling()) {
      UCallExpression e = UastContextKt.toUElement(child, UCallExpression.class);
      if (e != null) return e;
    }
    return null;
  }

  public boolean isValid() {
    return myProperty != null && myProperty.getPsiElement().isValid() && (myCallExpression == null || myCallExpression.isPsiValid());
  }

  public VirtualFile getFile() {
    PsiFile file = getPsiFile();
    return file == null ? null : file.getVirtualFile();
  }

  public PsiFile getPsiFile() {
    return myProperty.getPsiElement().getContainingFile();
  }

  public String getKey() {
    return myProperty.getKey();
  }

  public String getValue() {
    return myProperty.getValue();
  }

  public String getPlaceholder() {
    return myCallExpression == null ? '"' + getValue() + '"' : PropertyFoldingBuilder.format(myCallExpression).first;
  }

  public void setValue(String newValue) {
    myProperty.setValue(newValue);
  }

  public int placeholderToValueOffset(int offset) {
    if (myCallExpression == null) return offset - 1;
    List<Couple<Integer>> replacements = PropertyFoldingBuilder.format(myCallExpression).second;
    if (replacements == null) return offset - 1;
    Iterator<Couple<Integer>> it = replacements.iterator();
    int diff = 0;
    while (it.hasNext()) {
      Couple<Integer> start = it.next();
      Couple<Integer> end = it.next();
      if (offset <= start.second) return offset + start.first - start.second - 1;
      if (offset < end.second) return end.first - 2;
      diff = end.second - end.first;
    }
    return offset - diff - 1;
  }

  public int valueToPlaceholderOffset(int offset) {
    offset++;
    if (myCallExpression == null) return offset;
    Pair<String, List<Couple<Integer>>> info = PropertyFoldingBuilder.format(myCallExpression);
    List<Couple<Integer>> replacements = info.second;
    if (replacements == null) return offset;
    Iterator<Couple<Integer>> it = replacements.iterator();
    int diff = 0;
    while (it.hasNext()) {
      Couple<Integer> start = it.next();
      Couple<Integer> end = it.next();
      if (offset <= start.first) return offset - start.first + start.second;
      if (offset < end.first) return end.second - 1;
      diff = end.second - end.first;
    }
    return offset + diff;
  }
}
