// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;

public class ModifiersTest extends LightGroovyTestCase {
  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  public void test_top_level_enum_is_not_static() {
    getFixture().addFileToProject("classes.groovy", "enum E {}");
    PsiClass enumClass = getFixture().findClass("E");
    assert !enumClass.hasModifierProperty(PsiModifier.STATIC);
  }

  public void test_inner_enum_is_static() {
    getFixture().addFileToProject("classes.groovy", "class Outer { enum E {} }");
    PsiClass enumClass = getFixture().findClass("Outer.E");
    assert enumClass.hasModifierProperty(PsiModifier.STATIC);
  }
}
