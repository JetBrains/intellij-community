// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;

public class GrUnnecessaryPublicModifierInspectionTest extends LightGroovyTestCase {
  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  public void testHighlightingAndFix() {
    myFixture.enableInspections(GrUnnecessaryPublicModifierInspection.class);
    myFixture.configureByText("_.groovy", """
      <warning descr="Modifier 'public' is not necessary">pu<caret>blic</warning> class A {
      
          <warning descr="Modifier 'public' is not necessary">public</warning> A() {}
      
          <warning descr="Modifier 'public' is not necessary">public</warning> foo() {}
      
          public x
      
          <warning descr="Modifier 'public' is not necessary">public</warning> class B {}
      }
      
      <warning descr="Modifier 'public' is not necessary">public</warning> enum E {}
      
      <warning descr="Modifier 'public' is not necessary">public</warning> interface I {}
      """);
    myFixture.checkHighlighting();
    myFixture.launchAction(myFixture.findSingleIntention("Fix all 'Unnecessary 'public''"));
    myFixture.checkResult("""
                            class A {
                            
                                A() {}
                            
                                def foo() {}
                            
                                public x
                            
                                class B {}
                            }
                            
                            enum E {}
                            
                            interface I {}
                            """);
  }
}