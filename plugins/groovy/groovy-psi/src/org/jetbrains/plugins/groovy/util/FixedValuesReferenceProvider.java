// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FixedValuesReferenceProvider extends PsiReferenceProvider {

  private final String[] myValues;

  private final boolean mySoft;

  public FixedValuesReferenceProvider(String @NotNull [] values) {
    this(values, false);
  }

  public FixedValuesReferenceProvider(String @NotNull [] values, boolean soft) {
    myValues = values;
    mySoft = soft;
  }

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    return new PsiReference[]{
      new PsiReferenceBase<>(element, mySoft) {
        @Override
        public @Nullable PsiElement resolve() {
          return null;
        }

        @Override
        public Object @NotNull [] getVariants() {
          return myValues;
        }
      }
    };
  }
}
