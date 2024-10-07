// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes;

import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase;

public class GrAddAnnotationFixTest extends GrHighlightingTestBase {
  @Override
  public @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  public void testAddAnnotation() {
    @Language("Groovy")
    String text = """
    class Cls {
      static String s() {
        return "hello"
      }
    }
    """;
    myFixture.addClass("package anno;\n\nimport java.lang.annotation.*;\n\n@Target(ElementType.TYPE_USE) public @interface Anno { }");
    myFixture.configureByText("Cls.groovy", text);
    PsiMethod method = ((PsiClassOwner)myFixture.getFile()).getClasses()[0].getMethods()[0];
    AddAnnotationPsiFix fix = new AddAnnotationPsiFix("anno.Anno", method);
    fix.applyFix();
    myFixture.checkResult("""
                            import anno.Anno
                            
                            class Cls {
                              @Anno
                              static String s() {
                                return "hello"
                              }
                            }
                            """);
  }
}