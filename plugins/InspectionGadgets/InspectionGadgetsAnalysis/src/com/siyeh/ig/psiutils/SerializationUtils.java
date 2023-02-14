/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SerializationUtils {

  private SerializationUtils() {}

  public static boolean isSerializable(@Nullable PsiClass aClass) {
    return InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_IO_SERIALIZABLE);
  }

  public static boolean isExternalizable(@Nullable PsiClass aClass) {
    return InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_IO_EXTERNALIZABLE);
  }

  public static boolean isDirectlySerializable(@NotNull PsiClass aClass) {
    final PsiReferenceList implementsList = aClass.getImplementsList();
    if (implementsList == null) {
      return false;
    }
    for (PsiJavaCodeReferenceElement aInterfaces : implementsList.getReferenceElements()) {
      PsiElement implemented = aInterfaces.resolve();
      if (implemented instanceof PsiClass && CommonClassNames.JAVA_IO_SERIALIZABLE.equals(((PsiClass)implemented).getQualifiedName())) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasReadObject(@NotNull PsiClass aClass) {
    final PsiMethod[] methods = aClass.findMethodsByName("readObject", false);
    for (final PsiMethod method : methods) {
      if (isReadObject(method)) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasReadResolve(@NotNull PsiClass aClass) {
    final PsiMethod[] methods = aClass.findMethodsByName("readResolve", true);
    for (PsiMethod method : methods) {
      if (isReadResolve(method)) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasWriteObject(@NotNull PsiClass aClass) {
    final PsiMethod[] methods = aClass.findMethodsByName("writeObject", false);
    for (final PsiMethod method : methods) {
      if (isWriteObject(method)) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasWriteReplace(@NotNull PsiClass aClass) {
    final PsiMethod[] methods = aClass.findMethodsByName("writeReplace", true);
    for (PsiMethod method : methods) {
      if (isWriteReplace(method)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isReadObject(@NotNull PsiMethod method) {
    final PsiClassType type = TypeUtils.getType("java.io.ObjectInputStream", method);
    return MethodUtils.methodMatches(method, null, PsiTypes.voidType(), "readObject", type);
  }

  public static boolean isWriteObject(@NotNull PsiMethod method) {
    final PsiClassType type = TypeUtils.getType("java.io.ObjectOutputStream", method);
    return MethodUtils.methodMatches(method, null, PsiTypes.voidType(), "writeObject", type);
  }

  public static boolean isReadObjectNoData(@NotNull PsiMethod method) {
    return MethodUtils.methodMatches(method, null, PsiTypes.voidType(), "readObjectNoData");
  }

  public static boolean isReadResolve(@NotNull PsiMethod method) {
    return MethodUtils.simpleMethodMatches(method, null, CommonClassNames.JAVA_LANG_OBJECT, "readResolve");
  }

  public static boolean isWriteReplace(@NotNull PsiMethod method) {
    return MethodUtils.simpleMethodMatches(method, null, CommonClassNames.JAVA_LANG_OBJECT, "writeReplace");
  }

  public static boolean isReadExternal(@NotNull PsiMethod method) {
    final PsiClassType type = TypeUtils.getType("java.io.ObjectInput", method);
    return MethodUtils.methodMatches(method, null, PsiTypes.voidType(), "readExternal", type);
  }

  public static boolean isWriteExternal(@NotNull PsiMethod method) {
    final PsiClassType type = TypeUtils.getType("java.io.ObjectOutput", method);
    return MethodUtils.methodMatches(method, null, PsiTypes.voidType(), "writeExternal", type);
  }

  public static boolean isSerialVersionUid(@NotNull PsiField field) {
    return isConstant(field)
           && field.getName().equals(CommonClassNames.SERIAL_VERSION_UID_FIELD_NAME)
           && field.getType().equals(PsiTypes.longType());
  }

  public static boolean isSerialPersistentFields(@NotNull PsiField field) {
    return isConstant(field) && field.getName().equals("serialPersistentFields") &&
           field.getType().equalsToText("java.io.ObjectStreamField[]");
  }

  private static boolean isConstant(@NotNull PsiField field) {
    return field.hasModifierProperty(PsiModifier.PRIVATE)
           && field.hasModifierProperty(PsiModifier.STATIC)
           && field.hasModifierProperty(PsiModifier.FINAL);
  }

  public static boolean isProbablySerializable(PsiType type) {
    if (type instanceof PsiWildcardType || type instanceof PsiPrimitiveType) {
      return true;
    }
    if (type instanceof PsiArrayType arrayType) {
      final PsiType componentType = arrayType.getComponentType();
      return isProbablySerializable(componentType);
    }
    if (type instanceof PsiClassType classType) {
      final PsiClass aClass = classType.resolve();
      if (aClass instanceof PsiTypeParameter typeParameter) {
        final PsiReferenceList extendsList = typeParameter.getExtendsList();
        return ContainerUtil.and(extendsList.getReferencedTypes(), SerializationUtils::isProbablySerializable);
      }
      if (aClass == null || aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        // to avoid false positives
        return true;
      }
      if (isSerializable(aClass) || isExternalizable(aClass)) {
        return true;
      }
      if (InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_COLLECTION) ||
          InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_MAP)) {
        return ContainerUtil.and(classType.getParameters(), SerializationUtils::isProbablySerializable);
      }
      return false;
    }
    return false;
  }
}
