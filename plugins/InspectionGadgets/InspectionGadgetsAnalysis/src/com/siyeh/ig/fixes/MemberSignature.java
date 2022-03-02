/*
 * Copyright 2003-2015 Bas Leijdekkers
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
package com.siyeh.ig.fixes;

import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Modifier;

public class MemberSignature implements Comparable<MemberSignature> {
  @NonNls private static final String CONSTRUCTOR_NAME = "<init>";
  @NonNls private static final String INITIALIZER_SIGNATURE = "()V";
  @NonNls private static final MemberSignature ASSERTIONS_DISABLED_FIELD =
    new MemberSignature("$assertionsDisabled", Modifier.STATIC | Modifier.FINAL, "Z");
  @NonNls private static final MemberSignature PACKAGE_PRIVATE_CONSTRUCTOR =
    new MemberSignature(CONSTRUCTOR_NAME, 0, INITIALIZER_SIGNATURE);
  @NonNls private static final MemberSignature PUBLIC_CONSTRUCTOR =
    new MemberSignature(CONSTRUCTOR_NAME, Modifier.PUBLIC, INITIALIZER_SIGNATURE);
  @NonNls private static final MemberSignature STATIC_INITIALIZER =
    new MemberSignature("<clinit>", Modifier.STATIC, INITIALIZER_SIGNATURE);

  private final int modifiers;
  private final String name;
  private final String signature;

  public MemberSignature(PsiField field) {
    modifiers = calculateModifierBitmap(field.getModifierList());
    name = field.getName();
    signature = ClassUtil.getBinaryPresentation(field.getType());
  }

  public MemberSignature(PsiMethod method) {
    modifiers = calculateModifierBitmap(method.getModifierList());
    signature = ClassUtil.getAsmMethodSignature(method).replace('/', '.');
    name = method.isConstructor() ? CONSTRUCTOR_NAME : method.getName();
  }

  public MemberSignature(@NonNls String name, int modifiers, @NonNls String signature) {
    this.name = name;
    this.modifiers = modifiers;
    this.signature = signature;
  }

  public static int calculateModifierBitmap(PsiModifierList modifierList) {
    int modifiers = 0;
    if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
      modifiers |= Modifier.PUBLIC;
    }
    if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
      modifiers |= Modifier.PRIVATE;
    }
    if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
      modifiers |= Modifier.PROTECTED;
    }
    if (modifierList.hasModifierProperty(PsiModifier.STATIC)) {
      modifiers |= Modifier.STATIC;
    }
    if (modifierList.hasModifierProperty(PsiModifier.FINAL)) {
      modifiers |= Modifier.FINAL;
    }
    if (modifierList.hasModifierProperty(PsiModifier.VOLATILE)) {
      modifiers |= Modifier.VOLATILE;
    }
    if (modifierList.hasModifierProperty(PsiModifier.TRANSIENT)) {
      modifiers |= Modifier.TRANSIENT;
    }
    if (modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) {
      modifiers |= Modifier.ABSTRACT;
    }
    if (modifierList.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
      modifiers |= Modifier.SYNCHRONIZED;
    }
    if (modifierList.hasModifierProperty(PsiModifier.NATIVE)) {
      modifiers |= Modifier.NATIVE;
    }
    if (modifierList.hasModifierProperty(PsiModifier.STRICTFP)) {
      modifiers |= Modifier.STRICT;
    }
    return modifiers;
  }

  @Override
  public int compareTo(MemberSignature other) {
    final int result = name.compareTo(other.name);
    if (result != 0) {
      return result;
    }
    return signature.compareTo(other.signature);
  }

  /**
   * @deprecated use {@link ClassUtil#getBinaryPresentation(PsiType)} instead
   */
  @Deprecated(forRemoval = true)
  public static String createTypeSignature(PsiType type) {
    return ClassUtil.getBinaryPresentation(type);
  }

  public boolean equals(Object object) {
    try {
      final MemberSignature other = (MemberSignature)object;
      return name.equals(other.name) &&
             signature.equals(other.signature) &&
             modifiers == other.modifiers;
    }
    catch (ClassCastException | NullPointerException ignored) {
      return false;
    }
  }

  public static MemberSignature getAssertionsDisabledFieldMemberSignature() {
    return ASSERTIONS_DISABLED_FIELD;
  }

  public int getModifiers() {
    return modifiers;
  }

  public String getName() {
    return name;
  }

  public static MemberSignature getPackagePrivateConstructor() {
    return PACKAGE_PRIVATE_CONSTRUCTOR;
  }

  public static MemberSignature getPublicConstructor() {
    return PUBLIC_CONSTRUCTOR;
  }

  public String getSignature() {
    return signature;
  }

  public static MemberSignature getStaticInitializerMemberSignature() {
    return STATIC_INITIALIZER;
  }

  public int hashCode() {
    return name.hashCode() + signature.hashCode();
  }

  public String toString() {
    return name + signature;
  }
}