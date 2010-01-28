/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.stubs.impl;

import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.cache.ModifierFlags;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrModifierListStub;

/**
 * @author Maxim.Medvedev
 */
public class GrModifierListStubImpl extends StubBase<GrModifierList> implements GrModifierListStub {
  private final int myFlags;

  public GrModifierListStubImpl(StubElement parent, IStubElementType elementType, int flags) {
    super(parent, elementType);
    this.myFlags = flags;
  }

  public int getModifiersFlags() {
    return myFlags;
  }

  public static int buildFlags(GrModifierList modifierList) {
    int flags = 0;
    if (modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) {
      flags |= ModifierFlags.ABSTRACT_MASK;
    }
    if (modifierList.hasModifierProperty(PsiModifier.FINAL)) {
      flags |= ModifierFlags.FINAL_MASK;
    }
    if (modifierList.hasModifierProperty(PsiModifier.NATIVE)) {
      flags |= ModifierFlags.NATIVE_MASK;
    }
    if (modifierList.hasModifierProperty(PsiModifier.STATIC)) {
      flags |= ModifierFlags.STATIC_MASK;
    }
    if (modifierList.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
      flags |= ModifierFlags.SYNCHRONIZED_MASK;
    }
    if (modifierList.hasModifierProperty(PsiModifier.TRANSIENT)) {
      flags |= ModifierFlags.TRANSIENT_MASK;
    }
    if (modifierList.hasModifierProperty(PsiModifier.VOLATILE)) {
      flags |= ModifierFlags.VOLATILE_MASK;
    }
    if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
      flags |= ModifierFlags.PRIVATE_MASK;
    }
    if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
      flags |= ModifierFlags.PROTECTED_MASK;
    }
    if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
      flags |= ModifierFlags.PUBLIC_MASK;
    }
    if (modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
      flags |= ModifierFlags.PACKAGE_LOCAL_MASK;
    }
    if (modifierList.hasModifierProperty(PsiModifier.STRICTFP)) {
      flags |= ModifierFlags.STRICTFP_MASK;
    }
    return flags;
  }
  
}
