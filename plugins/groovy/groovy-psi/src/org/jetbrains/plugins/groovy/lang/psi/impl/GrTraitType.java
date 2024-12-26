// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeVisitor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GrTraitType extends PsiType {

  private final @NotNull PsiIntersectionType myDelegate;
  private final @NotNull PsiType myExprType;
  private final @NotNull List<PsiType> myTraitTypes;

  private GrTraitType(@NotNull PsiIntersectionType delegate) {
    super(PsiAnnotation.EMPTY_ARRAY);
    myDelegate = delegate;
    myExprType = delegate.getConjuncts()[0];
    myTraitTypes = ContainerUtil.subArrayAsList(delegate.getConjuncts(), 1, delegate.getConjuncts().length);
  }

  public @NotNull PsiType getExprType() {
    return myExprType;
  }

  public @NotNull @Unmodifiable List<PsiType> getTraitTypes() {
    return myTraitTypes;
  }

  public PsiType @NotNull [] getConjuncts() {
    return myDelegate.getConjuncts();
  }

  @Override
  public @NotNull String getPresentableText() {
    return myExprType.getPresentableText() + " as " + StringUtil.join(ContainerUtil.map(myTraitTypes, type -> type.getPresentableText()), ", ");
  }

  @Override
  public @NotNull String getCanonicalText() {
    return myDelegate.getCanonicalText();
  }

  @Override
  public @NlsSafe @NotNull String getInternalCanonicalText() {
    return myExprType.getCanonicalText() + " as " + StringUtil.join(ContainerUtil.map(myTraitTypes, type -> type.getInternalCanonicalText()), ", ");
  }

  @Override
  public boolean isValid() {
    return myDelegate.isValid();
  }

  @Override
  public boolean equalsToText(@NotNull @NonNls String text) {
    return myDelegate.equalsToText(text);
  }

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    return myDelegate.accept(visitor);
  }

  @Override
  public @Nullable GlobalSearchScope getResolveScope() {
    return myDelegate.getResolveScope();
  }

  @Override
  public PsiType @NotNull [] getSuperTypes() {
    return myDelegate.getSuperTypes();
  }

  public static @NotNull PsiType createTraitType(@NotNull PsiType type, @NotNull List<? extends PsiType> traits) {
    return createTraitType(ContainerUtil.prepend(traits, type instanceof GrTraitType ? ((GrTraitType)type).myDelegate : type));
  }

  public static @NotNull PsiType createTraitType(@NotNull List<PsiType> types) {
    return createTraitType(types.toArray(PsiType.createArray(types.size())));
  }

  public static @NotNull PsiType createTraitType(PsiType @NotNull [] types) {
    final Set<PsiType> flattened = PsiIntersectionType.flatten(types, new LinkedHashSet<>() {
      @Override
      public boolean add(PsiType type) {
        remove(type);
        return super.add(type);
      }
    });
    final PsiType[] conjuncts = flattened.toArray(PsiType.createArray(flattened.size()));
    if (conjuncts.length == 1) {
      return conjuncts[0];
    }
    else {
      return new GrTraitType((PsiIntersectionType)PsiIntersectionType.createIntersection(false, conjuncts));
    }
  }
}
