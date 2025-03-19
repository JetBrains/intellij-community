// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.modifiers;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.junit.Assert;

import java.util.List;

import static com.intellij.psi.PsiModifier.*;

public abstract class GrVisibilityTestBase extends LightGroovyTestCase {
  protected PsiClass addClass(String packageName, String text) {
    String fileText = "package " + packageName + "\n" +
                      """
                        import groovy.transform.PackageScope
                        import static groovy.transform.PackageScopeTarget.*
                        """ +
                      text;
    GroovyFile file = (GroovyFile)getFixture().addFileToProject(packageName + "/_.groovy",
                                                                fileText);
    return file.getTypeDefinitions()[0];
  }

  protected PsiClass addClass(String text) {
    return addClass("pckg", text);
  }

  protected static void assertVisibility(@NotNull PsiModifierListOwner listOwner,
                                         @ModifierConstant @NonNls @NotNull String modifier) {
    Assert.assertTrue(listOwner.hasModifierProperty(modifier));
    for (@ModifierConstant String visibilityModifier : VISIBILITY_MODIFIERS) {
      if (modifier.equals(visibilityModifier)) continue;
      Assert.assertFalse(listOwner.hasModifierProperty(visibilityModifier));
    }
    PsiFileBase file = (PsiFileBase)listOwner.getContainingFile();
    Assert.assertFalse(file.isContentsLoaded());
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  private static final List<String> VISIBILITY_MODIFIERS = List.of(PUBLIC, PRIVATE, PROTECTED, PACKAGE_LOCAL);
  private final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST;
}
