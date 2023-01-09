// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.features;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.usages.similarity.bag.Bag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class UsageSimilarityFeaturesRecorder {
  private final @NotNull Bag myFeatures;
  private final @Nullable PsiElement myInitialContext;
  private final @NotNull PsiElement myUsage;

  public UsageSimilarityFeaturesRecorder(@Nullable PsiElement initialContext, @NotNull PsiElement usage) {
    myUsage = usage;
    myFeatures = new Bag();
    myInitialContext = initialContext;
  }

  private boolean isInSameTreeAsUsage(@NotNull PsiElement expression) {
    return PsiTreeUtil.findFirstParent(
      expression,
      false, element -> element == myUsage || element == myInitialContext) == myUsage || PsiTreeUtil.findFirstParent(
      myUsage,
      false, element -> element == expression || element == myInitialContext) == expression;
  }

  public @NotNull Bag getFeatures() {
    return myFeatures;
  }

  public void addFeature(@NotNull String tokenFeature) {
    myFeatures.add(tokenFeature);
  }

  public void addAllFeatures(@NotNull PsiElement element, @Nullable String tokenFeature) {
    if (tokenFeature == null) {
      tokenFeature = PsiUtilCore.getElementType(element).toString();
    }
    if (Registry.is("similarity.distinguish.usages.in.one.statement")) {
      tokenFeature = (isInSameTreeAsUsage(element) ? "USAGE: " : "CONTEXT: ") + tokenFeature;
    }
    myFeatures.add(tokenFeature);
    if (element == myInitialContext) {
      return;
    }
    PsiElement parent = element.getParent();
    if (parent != myInitialContext) {
      addParentFeatures(parent, tokenFeature, "GP:");
    }
    addParentFeatures(element, tokenFeature, "P:");
    addSiblingFeatures(element, tokenFeature);
  }

  private void addParentFeatures(@NotNull PsiElement element, @Nullable String tokenFeature, @NotNull String prefix) {
    if (!Registry.is("similarity.find.usages.use.parent.features")) {
      return;
    }
    PsiElement parent = element.getParent();
    if (parent != null) {
      myFeatures.add(tokenFeature + " " + prefix + " " + PsiUtilCore.getElementType(parent) + " " + getChildNumber(parent, element));
    }
  }

  private void addSiblingFeatures(@NotNull PsiElement element, @Nullable String tokenFeature) {
    if (!Registry.is("similarity.find.usages.use.sibling.features")) {
      return;
    }
    myFeatures.add(tokenFeature + " PREV: " + getPrevMeaningfulSibling(element));
    myFeatures.add(tokenFeature + " NEXT: " + getNextMeaningfulSibling(element));
  }

  private static int getChildNumber(@NotNull PsiElement parent, @NotNull PsiElement child) {
    if (!Registry.is("similarity.find.usages.use.parent.features.with.child.number")) return -1;
    PsiElement[] children = Arrays.stream(parent.getChildren()).filter(e -> !(e instanceof PsiWhiteSpace)).toArray(PsiElement[]::new);
    for (int i = 0; i < children.length; i++) {
      if (children[i] == child) {
        return i;
      }
    }
    return -1;
  }

  private static @Nullable PsiElement getNextMeaningfulSibling(@NotNull PsiElement element) {
    PsiElement nextSibling = element.getNextSibling();
    if (nextSibling != null) {
      while (nextSibling instanceof PsiWhiteSpace || nextSibling instanceof PsiComment) {
        nextSibling = nextSibling.getNextSibling();
      }
    }
    return nextSibling;
  }

  private static @Nullable PsiElement getPrevMeaningfulSibling(@NotNull PsiElement element) {
    PsiElement prevSibling = element.getPrevSibling();
    if (prevSibling != null) {
      while (prevSibling instanceof PsiWhiteSpace || prevSibling instanceof PsiComment) {
        prevSibling = prevSibling.getPrevSibling();
      }
    }
    return prevSibling;
  }
}
