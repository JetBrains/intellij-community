// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import org.jetbrains.idea.devkit.inspections.internal.UsePrimitiveTypesEqualsInspection;

public abstract class UsePsiPrimitiveTypeEqualsFixTestBase extends LightDevKitInspectionFixTestBase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("""
      package com.intellij.lang.jvm.types;
      public final class JvmPrimitiveTypeKind {}
      """);
    myFixture.addClass("""
      package com.intellij.psi;
      import org.jetbrains.annotations.Nullable;
      import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind;
      public final class PsiPrimitiveType extends PsiType {
        PsiPrimitiveType(@Nullable JvmPrimitiveTypeKind kind) {}
      }
      """);
    //noinspection StaticInitializerReferencesSubClass
    myFixture.addClass("""
      package com.intellij.psi;
      public abstract class PsiType {
        public static final PsiPrimitiveType BYTE = new PsiPrimitiveType(null);
        public static final PsiPrimitiveType DOUBLE = new PsiPrimitiveType(null);
      }
      """);
    myFixture.enableInspections(new UsePrimitiveTypesEqualsInspection());
  }
}
