// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Folds '{@code {n}}' placeholders in .properties file to corresponding arguments from Java code.
 *
 * For example, given two files:
 *
 * <pre>
 * <b>myBundle.properties</b>:
 * {@code extract.name=Extract {0} name to {1} {2}}
 * <b>A.java</b>:
 * {@code MyBundle.message("extract.name", "method", "interface", "I.java")}
 * </pre>
 * This builder will produce these foldings:
 * <pre>
 * <b>myBundle.properties</b>:
 * {@code extract.name=Extract +method+ name to +interface+ +I.java+}</pre>
 */
public class ResourceBundleContextFoldingBuilder extends FoldingBuilderEx {
  @Override
  public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
    if (!PropertiesFoldingSettings.getInstance().isFoldPlaceholdersToContext()) {
      return FoldingDescriptor.EMPTY;
    }
    List<FoldingDescriptor> result = new ArrayList<>();
    for (IProperty property : ((PropertiesFile)root).getProperties()) {
      String value = property.getValue();
      if (value != null && value.contains("{0}")) {
        fold(property, result);
      }
    }
    return result.toArray(FoldingDescriptor.EMPTY);
  }

  private static void fold(@NotNull IProperty property, @NotNull List<? super FoldingDescriptor> result) {
    ReferencesSearch.search(property.getPsiElement()).forEach((PsiReference reference) -> !tryToFoldReference(reference, property, result));
  }

  // return true if folded successfully
  private static boolean tryToFoldReference(@NotNull PsiReference reference,
                                            @NotNull IProperty property,
                                            @NotNull List<? super FoldingDescriptor> result) {
    int before = result.size();
    PsiElement referenceElement = reference.getElement();
    // all arguments[i+1..] are considered template arguments
    String key = property.getUnescapedKey();
    if (key == null) return false;
    PsiElement psiElement = property.getPsiElement();
    String text = psiElement.getText();
    PsiElement[] arguments = referenceElement.getLanguage().getID().equals("kotlin") ?
                             getKotlinArguments(referenceElement) : getJavaArguments(referenceElement);
    if (arguments == null) return false;
    for (int i=0; i<arguments.length; i++) {
      PsiElement argument = arguments[i];
      String templateText = "{" + i + "}";
      int offset = text.indexOf(templateText, key.length());
      if (offset != -1) {
        int start = psiElement.getTextRange().getStartOffset();
        result.add(new FoldingDescriptor(psiElement, start+ offset, start+offset+templateText.length(), null, (argument.getText())));
      }
    }
    return result.size() != before;
  }

  /**
   * return arguments of the method referencing this property.
   * E.g. for {@code MyBundle.message("prop", expr1, argX)} return (expr1, argX)
   */
  private static PsiElement[] getJavaArguments(@NotNull PsiElement referenceElement) {
    PsiExpression expression = PsiTreeUtil.getParentOfType(referenceElement, PsiExpression.class, false);
    if (expression == null) return null;
    PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiExpressionList)) return null;
    PsiExpression[] arguments = ((PsiExpressionList)parent).getExpressions();
    int i = ArrayUtil.indexOf(arguments, expression);
    if (i == -1) return null;
    return Arrays.copyOfRange(arguments, i + 1, arguments.length, PsiElement[].class);
  }

  // since kotlin and java method calls have different psi structure and no common UAST,
  // we have to have two methods for finding Java/Kotlin code referencing this property
  private static PsiElement[] getKotlinArguments(@NotNull PsiElement referenceElement) {
    PsiElement expression = referenceElement.getParent();
    if (expression == null) return null;
    List<PsiElement> arguments = new ArrayList<>();
    for (PsiElement e = expression.getNextSibling(); e != null; e = e.getNextSibling()) {
      if (e instanceof PsiComment || e instanceof LeafPsiElement) continue;
      arguments.add(e);
    }
    if (arguments.isEmpty()) {
      return null;
    }
    return arguments.toArray(PsiElement.EMPTY_ARRAY);
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return true;
  }

  @Override
  public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
    return null;
  }
}
