/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.util.ArrayUtil;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */

public class GrPropertyForCompletion extends LightFieldBuilder {
  @NotNull private final PsiMethod myOriginalAccessor;

  public GrPropertyForCompletion(@NotNull PsiMethod method, @NotNull String name, @NotNull PsiType type) {
    super(method.getManager(), name, type);
    myOriginalAccessor = method;

    List<String> modifiers = new ArrayList<>();
    if (method.hasModifierProperty(GrModifier.PUBLIC)) modifiers.add(GrModifier.PUBLIC);
    if (method.hasModifierProperty(GrModifier.PROTECTED)) modifiers.add(GrModifier.PROTECTED);
    if (method.hasModifierProperty(GrModifier.PRIVATE)) modifiers.add(GrModifier.PRIVATE);
    if (method.hasModifierProperty(GrModifier.STATIC)) modifiers.add(GrModifier.STATIC);

    setContainingClass(method.getContainingClass());
    setModifiers(ArrayUtil.toStringArray(modifiers));
    setBaseIcon(JetgroovyIcons.Groovy.Property);
  }

  @NotNull
  public PsiMethod getOriginalAccessor() {
    return myOriginalAccessor;
  }

  @Override
  public int hashCode() {
    final int isStatic = hasModifierProperty(GrModifier.STATIC) ? 1 : 0;
    final int visibilityModifier;
    visibilityModifier = getVisibilityCode();

    return getName().hashCode() << 3 + isStatic << 2 + visibilityModifier;
  }

  private int getVisibilityCode() {
    int visibilityModifier;
    if (hasModifierProperty(GrModifier.PUBLIC)) {
      visibilityModifier = 3;
    }
    else if (hasModifierProperty(GrModifier.PROTECTED)) {
      visibilityModifier = 2;
    }
    else if (hasModifierProperty(GrModifier.PRIVATE)) {
      visibilityModifier = 1;
    }
    else {
      visibilityModifier = 0;
    }
    return visibilityModifier;
  }

  @NotNull
  @Override
  public String getName() {
    return super.getName();
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    if (!(another instanceof GrPropertyForCompletion)) return false;
    if (!((GrPropertyForCompletion)another).getName().equals(getName())) return false;
    if (hasModifierProperty(GrModifier.STATIC) != ((GrPropertyForCompletion)another).hasModifierProperty(GrModifier.STATIC)) return false;
    if (getVisibilityCode() != ((GrPropertyForCompletion)another).getVisibilityCode()) return false;
/*    final PsiClass containingClass = getContainingClass();
    final PsiClass anotherClass = ((GrPropertyForCompletion)another).getContainingClass();
    return containingClass == null && anotherClass == null || getManager().areElementsEquivalent(containingClass, anotherClass);*/
    return true;
  }
}
