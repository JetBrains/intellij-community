// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.modifiers;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.util.List;

import static com.intellij.psi.PsiModifier.ModifierConstant;
import static com.intellij.psi.PsiModifier.PACKAGE_LOCAL;
import static com.intellij.psi.PsiModifier.PRIVATE;
import static com.intellij.psi.PsiModifier.PROTECTED;
import static com.intellij.psi.PsiModifier.PUBLIC;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The helpers of the Groovy visibility tests.
 */
public final class GrVisibilityTestUtil {
  private static final List<String> VISIBILITY_MODIFIERS = List.of(PUBLIC, PRIVATE, PROTECTED, PACKAGE_LOCAL);

  private GrVisibilityTestUtil() {
  }

  /**
   * Adds {@code <packageName>/_.groovy} with the imports of {@code PackageScope} and {@code text}, and returns its first class.
   */
  public static PsiClass addClass(JavaCodeInsightTestFixture fixture, String packageName, String text) {
    String fileText = "package " + packageName + "\n" +
                      """
                        import groovy.transform.PackageScope
                        import static groovy.transform.PackageScopeTarget.*
                        """ +
                      text;
    GroovyFile file = (GroovyFile)fixture.addFileToProject(packageName + "/_.groovy", fileText);
    return ReadAction.computeBlocking(() -> file.getTypeDefinitions()[0]);
  }

  public static PsiClass addClass(JavaCodeInsightTestFixture fixture, String text) {
    return addClass(fixture, "pckg", text);
  }

  /**
   * Checks that {@code listOwner} has the visibility {@code modifier} and no other visibility, and that its file has no loaded AST.
   */
  public static void assertVisibility(@NotNull PsiModifierListOwner listOwner, @ModifierConstant @NonNls @NotNull String modifier) {
    ReadAction.runBlocking(() -> {
      assertTrue(listOwner.hasModifierProperty(modifier));
      for (@ModifierConstant String visibilityModifier : VISIBILITY_MODIFIERS) {
        if (modifier.equals(visibilityModifier)) continue;
        assertFalse(listOwner.hasModifierProperty(visibilityModifier));
      }
      PsiFileBase file = (PsiFileBase)listOwner.getContainingFile();
      assertFalse(file.isContentsLoaded());
    });
  }
}
