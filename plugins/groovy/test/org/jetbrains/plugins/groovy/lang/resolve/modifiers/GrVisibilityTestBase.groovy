/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve.modifiers

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiModifierListOwner
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyLightProjectDescriptor
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile

import static com.intellij.psi.PsiModifier.*

@CompileStatic
abstract class GrVisibilityTestBase extends LightGroovyTestCase {

  private static final String[] VISIBILITY_MODIFIERS = [PUBLIC, PRIVATE, PROTECTED, PACKAGE_LOCAL]

  final LightProjectDescriptor projectDescriptor = GroovyLightProjectDescriptor.GROOVY_LATEST

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
