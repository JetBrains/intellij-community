// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GrVisibilityTestUtil")

package org.jetbrains.plugins.groovy.lang.resolve.modifiers

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

private val VISIBILITY_MODIFIERS = listOf(PsiModifier.PUBLIC, PsiModifier.PRIVATE, PsiModifier.PROTECTED, PsiModifier.PACKAGE_LOCAL)

/**
 * Adds the class [text] to the package [packageName] of [fixture], with the imports of `PackageScope`.
 * It replaces a helper of the JUnit 3 base `GrVisibilityTestBase`.
 */
@TestOnly
@JvmOverloads
fun addClass(fixture: JavaCodeInsightTestFixture, packageName: String = "pckg", text: String): PsiClass {
  val fileText = "package $packageName\n" +
                 "import groovy.transform.PackageScope\n" +
                 "import static groovy.transform.PackageScopeTarget.*\n" +
                 text
  val file = fixture.addFileToProject("$packageName/_.groovy", fileText) as GroovyFile
  return ReadAction.computeBlocking<PsiClass, Throwable> { file.typeDefinitions[0] }
}

/** Checks that [listOwner] has only the visibility [modifier], and that the check does not load the contents of its file. */
@TestOnly
fun assertVisibility(listOwner: PsiModifierListOwner, @PsiModifier.ModifierConstant modifier: String) {
  assertTrue(listOwner.hasModifierProperty(modifier))
  for (visibilityModifier in VISIBILITY_MODIFIERS) {
    if (modifier == visibilityModifier) continue
    assertFalse(listOwner.hasModifierProperty(visibilityModifier))
  }
  assertFalse((listOwner.containingFile as PsiFileBase).isContentsLoaded)
}
