// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.modifiers

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiModifierListOwner
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile

import static com.intellij.psi.PsiModifier.*

@CompileStatic
abstract class GrVisibilityTestBase extends LightGroovyTestCase {

  private static final String[] VISIBILITY_MODIFIERS = [PUBLIC, PRIVATE, PROTECTED, PACKAGE_LOCAL]

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  protected PsiClass addClass(String packageName = "pckg", String text) {
    def file = fixture.addFileToProject("$packageName/_.groovy", """\
package $packageName
import groovy.transform.PackageScope
import static groovy.transform.PackageScopeTarget.*

$text
""") as GroovyFile
    file.typeDefinitions.first()
  }

  protected static void assertVisibility(PsiModifierListOwner listOwner, String modifier) {
    assert listOwner.hasModifierProperty(modifier)
    (VISIBILITY_MODIFIERS - modifier).each {
      assert listOwner.hasModifierProperty(modifier)
    }
    def file = listOwner.containingFile as PsiFileBase
    assert !file.contentsLoaded
  }
}
