// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.modifiers;

import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Java access modifiers: public, protected, private, package-local
 */
enum AccessModifier {
  PUBLIC(PsiModifier.PUBLIC), PROTECTED(PsiModifier.PROTECTED), PACKAGE_LOCAL(PsiModifier.PACKAGE_LOCAL), PRIVATE(PsiModifier.PRIVATE);

  @NotNull @PsiModifier.ModifierConstant
  private final String myModifier;

  AccessModifier(@PsiModifier.ModifierConstant @NotNull String modifier) {
    myModifier = modifier;
  }

  /**
   * @return a {@link PsiModifier} string constant which corresponds to this access modifier.
   */
  @NotNull @PsiModifier.ModifierConstant
  public String toPsiModifier() {
    return myModifier;
  }

  /**
   * Checks whether given modifier owner has this access modifier (probably implicit)
   * @param owner element to check (e.g. class member)
   * @return true if it has current modifier
   */
  public boolean hasModifier(@NotNull PsiModifierListOwner owner) {
    return owner.hasModifierProperty(toPsiModifier());
  }

  /**
   * Returns an {@link AccessModifier} which corresponds to the given keyword;
   * null if supplied keyword is null or don't correspond to access modifier
   * @param keyword keyword to convert to access modifier
   * @return a corresponding access modifier
   */
  @Contract(value = "null -> null", pure = true)
  @Nullable
  public static AccessModifier fromKeyword(@Nullable PsiKeyword keyword) {
    return keyword == null ? null : fromPsiModifier(keyword.getText());
  }

  /**
   * Returns an {@link AccessModifier} which corresponds to the given String constant declared in
   * {@link PsiModifier} class.
   * @param modifier a modifier string
   * @return an access modifier or null if supplied string doesn't correspond to any access modifier.
   */
  @Contract(value = "null -> null", pure = true)
  @Nullable
  public static AccessModifier fromPsiModifier(@Nullable String modifier) {
    if (modifier == null) return null;
    switch (modifier) {
      case PsiModifier.PUBLIC:
        return PUBLIC;
      case PsiModifier.PROTECTED:
        return PROTECTED;
      case PsiModifier.PACKAGE_LOCAL:
        return PACKAGE_LOCAL;
      case PsiModifier.PRIVATE:
        return PRIVATE;
      default:
        return null;
    }
  }

  @Override
  public String toString() {
    return this == PACKAGE_LOCAL ? "package-private" : toPsiModifier();
  }
}
