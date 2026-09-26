// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.util.ArrayUtilRt;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */

public class GrPropertyForCompletion extends LightFieldBuilder {
  private final @NotNull PsiMethod myOriginalAccessor;

  public GrPropertyForCompletion(@NotNull PsiMethod method, @NotNull String name, @NotNull PsiType type) {
    super(method.getManager(), name, type);
    myOriginalAccessor = method;

    List<String> modifiers = new ArrayList<>();
    if (method.hasModifierProperty(PsiModifier.PUBLIC)) modifiers.add(PsiModifier.PUBLIC);
    if (method.hasModifierProperty(PsiModifier.PROTECTED)) modifiers.add(PsiModifier.PROTECTED);
    if (method.hasModifierProperty(PsiModifier.PRIVATE)) modifiers.add(PsiModifier.PRIVATE);
    if (method.hasModifierProperty(PsiModifier.STATIC)) modifiers.add(PsiModifier.STATIC);

    setContainingClass(method.getContainingClass());
    setModifiers(ArrayUtilRt.toStringArray(modifiers));
    setBaseIcon(JetgroovyIcons.Groovy.Property);
  }

  @Override
  public @NotNull Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }

  public @NotNull PsiMethod getOriginalAccessor() {
    return myOriginalAccessor;
  }

  @Override
  public int hashCode() {
    final int isStatic = hasModifierProperty(PsiModifier.STATIC) ? 1 : 0;
    final int visibilityModifier;
    visibilityModifier = getVisibilityCode();

    return getName().hashCode() << 3 + isStatic << 2 + visibilityModifier;
  }

  private int getVisibilityCode() {
    int visibilityModifier;
    if (hasModifierProperty(PsiModifier.PUBLIC)) {
      visibilityModifier = 3;
    }
    else if (hasModifierProperty(PsiModifier.PROTECTED)) {
      visibilityModifier = 2;
    }
    else if (hasModifierProperty(PsiModifier.PRIVATE)) {
      visibilityModifier = 1;
    }
    else {
      visibilityModifier = 0;
    }
    return visibilityModifier;
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    if (!(another instanceof GrPropertyForCompletion)) return false;
    if (!((GrPropertyForCompletion)another).getName().equals(getName())) return false;
    if (hasModifierProperty(PsiModifier.STATIC) != ((GrPropertyForCompletion)another).hasModifierProperty(PsiModifier.STATIC)) return false;
    if (getVisibilityCode() != ((GrPropertyForCompletion)another).getVisibilityCode()) return false;
/*    final PsiClass containingClass = getContainingClass();
    final PsiClass anotherClass = ((GrPropertyForCompletion)another).getContainingClass();
    return containingClass == null && anotherClass == null || getManager().areElementsEquivalent(containingClass, anotherClass);*/
    return true;
  }
}
