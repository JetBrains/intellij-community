// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes

import com.intellij.codeInsight.intention.AddAnnotationPsiFix
import com.intellij.psi.PsiClassOwner
import com.intellij.testFramework.LightProjectDescriptor
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

class GrAddAnnotationFixTest extends GrHighlightingTestBase {

  final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST

  void testAddAnnotation() {
    @Language("Groovy")
    def text = '''
class Cls {
    static String s() {
      return "hello";
    }
}
'''
    fixture.addClass("package anno;\n\nimport java.lang.annotation.*;\n\n@Target(ElementType.TYPE_USE) public @interface Anno { }");
    fixture.configureByText("Cls.groovy", text)
    def method = ((PsiClassOwner)fixture.file).classes[0].methods[0]
    def fix = new AddAnnotationPsiFix("anno.Anno", method)
    fix.applyFix()
    fixture.checkResult("""import anno.Anno

class Cls {
    @Anno
    static String s() {
      return "hello";
    }
}
""")
  }
}