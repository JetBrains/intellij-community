// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.*;
import com.intellij.psi.*;
import com.intellij.structuralsearch.impl.matcher.MatcherImplUtil;
import com.intellij.structuralsearch.impl.matcher.PatternTreeContext;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
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
    matchOptions.setSearchPattern(text);
    PsiElement[] elements =
      MatcherImplUtil.createTreeFromText(text, PatternTreeContext.Block, myPsiFile.getFileType(), myPsiFile.getProject());
    PsiElement psiElement = ContainerUtil.find(elements, element -> !(element instanceof PsiWhiteSpace));
    if (psiElement != null) {
      myShift = 2;
      psiElement.accept(visitor);
    }
    return myBuilder;
  }

  void replaceElement(@Nullable PsiElement element, PlaceholderCount count, boolean preferOriginal) {
    if (element == null) {
      return;
    }
    myBuilder.replaceRange(element.getTextRange().shiftLeft(myShift), new MyExpression(count.getPlaceholder(), element, preferOriginal));
  }

  private static class MyExpression extends Expression {

    private final String myPlaceholder;
    private final String myOriginalText;
    private final boolean myPreferOriginal;

    MyExpression(String placeholder, PsiElement original, boolean preferOriginal) {
      myPlaceholder = placeholder;
      myOriginalText = original.getText();
      myPreferOriginal = preferOriginal;
    }

    @Nullable
    @Override
    public Result calculateResult(ExpressionContext context) {
      return new TextResult(myPreferOriginal ? myOriginalText : myPlaceholder);
    }

    @Nullable
    @Override
    public Result calculateQuickResult(ExpressionContext context) {
      return calculateResult(context);
    }

    @Nullable
    @Override
    public LookupElement[] calculateLookupItems(ExpressionContext context) {
      LookupElement[] elements = {LookupElementBuilder.create(myPlaceholder), LookupElementBuilder.create(myOriginalText)};
      return myPreferOriginal ? ArrayUtil.reverseArray(elements) : elements;
    }
  }

  private static class PlaceholderCount {
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
