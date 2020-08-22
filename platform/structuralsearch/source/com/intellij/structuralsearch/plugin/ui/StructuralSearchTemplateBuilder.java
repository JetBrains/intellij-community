// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StructuralSearchTemplateBuilder {

  private final TemplateBuilder myBuilder;
  private final PsiFile myPsiFile;

  private final PlaceholderCount myClassCount = new PlaceholderCount("Class");
  private final PlaceholderCount myVarCount = new PlaceholderCount("Var");
  private final PlaceholderCount myFunCount = new PlaceholderCount("Fun");

  private int myShift;

  public StructuralSearchTemplateBuilder(@NotNull PsiFile psiFile) {
    myBuilder = TemplateBuilderFactory.getInstance().createTemplateBuilder(psiFile);
    myPsiFile = psiFile;
  }

  public TemplateBuilder buildTemplate() {

    JavaRecursiveElementVisitor visitor = new JavaRecursiveElementVisitor() {

      @Override
      public void visitIdentifier(PsiIdentifier identifier) {
        PsiElement parent = identifier.getParent();
        if (parent instanceof PsiClass) {
          replaceElement(identifier, myClassCount, true);
        }
        else if (parent instanceof PsiReferenceExpression) {
          if (parent.getParent() instanceof PsiMethodCallExpression)
            replaceElement(identifier, myFunCount, true);
          else
            replaceElement(identifier, myVarCount, false);
        }
        else if (parent instanceof PsiJavaCodeReferenceElement) {
          replaceElement(identifier, myClassCount, false);
        }
      }

      @Override
      public void visitReferenceList(PsiReferenceList list) {
        PsiJavaCodeReferenceElement[] elements = list.getReferenceElements();
        for (PsiJavaCodeReferenceElement element : elements) {
          replaceElement(element.getReferenceNameElement(), myClassCount, false);
        }
      }
    };

    MatchOptions matchOptions = new MatchOptions();
    String text = myPsiFile.getText();
    int textOffset = 0;
    while (textOffset < text.length() && StringUtil.isWhiteSpace(text.charAt(textOffset))) {
      textOffset++;
    }
    myShift -= textOffset;
    matchOptions.setSearchPattern(text);
    PsiElement[] elements =
      MatcherImplUtil.createTreeFromText(text, PatternTreeContext.Block, (LanguageFileType)myPsiFile.getFileType(), myPsiFile.getProject());
    if (elements.length > 0) {
      PsiElement element = elements[0];
      myShift += element.getTextRange().getStartOffset();
      element.accept(visitor);
    }
    return myBuilder;
  }

  void replaceElement(@Nullable PsiElement element, PlaceholderCount count, boolean preferOriginal) {
    if (element == null) {
      return;
    }
    String placeholder = count.getPlaceholder();
    String originalText = element.getText();
    LookupElement[] elements = {LookupElementBuilder.create(placeholder), LookupElementBuilder.create(originalText)};
    myBuilder.replaceRange(element.getTextRange().shiftLeft(myShift),
                           new ConstantNode(preferOriginal ? originalText : placeholder)
                             .withLookupItems(preferOriginal ? ArrayUtil.reverseArray(elements) : elements));
  }

  private static final class PlaceholderCount {
    private final String myName;
    private int myCount;

    private PlaceholderCount(String name) {
      myName = name;
    }

    public String getPlaceholder() {
      return "$" + myName + ++myCount + "$";
    }
  }
}
