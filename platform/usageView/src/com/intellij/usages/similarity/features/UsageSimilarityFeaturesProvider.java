// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.features;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.usages.similarity.bag.Bag;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface UsageSimilarityFeaturesProvider {
  @ApiStatus.Experimental
  ExtensionPointName<UsageSimilarityFeaturesProvider> EP_NAME = ExtensionPointName.create("com.intellij.usageFeaturesProvider");

  @RequiresReadLock
  @RequiresBackgroundThread
  @NotNull Bag getFeatures(@NotNull PsiElement usage);
}
