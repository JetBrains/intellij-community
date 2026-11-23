// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.internal.UsePrimitiveTypesEqualsInspection

/**
 * Creates a [LightDevKitInspectionFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 * The fixture enables [UsePrimitiveTypesEqualsInspection]. The project has the stubs of `PsiType` and `PsiPrimitiveType`.
 */
@JvmOverloads
fun usePsiPrimitiveTypeEqualsFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<LightDevKitInspectionFixture> = testFixture("usePsiPrimitiveTypeEqualsFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.addClass("""
    package com.intellij.lang.jvm.types;
    public final class JvmPrimitiveTypeKind {}
    """.trimIndent())
  fixture.addClass("""
    package com.intellij.psi;
    import org.jetbrains.annotations.Nullable;
    import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind;
    public final class PsiPrimitiveType extends PsiType {
      PsiPrimitiveType(@Nullable JvmPrimitiveTypeKind kind) {}
    }
    """.trimIndent())
  //noinspection StaticInitializerReferencesSubClass
  fixture.addClass("""
    package com.intellij.psi;
    public abstract class PsiType {
      public static final PsiPrimitiveType BYTE = new PsiPrimitiveType(null);
      public static final PsiPrimitiveType DOUBLE = new PsiPrimitiveType(null);
    }
    """.trimIndent())
  fixture.enableInspections(UsePrimitiveTypesEqualsInspection())
  initialized(devKit) {}
}
