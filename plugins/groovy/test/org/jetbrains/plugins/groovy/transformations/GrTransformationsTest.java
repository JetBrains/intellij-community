// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveTestCase;
import org.junit.Assert;

public class GrTransformationsTest extends GroovyResolveTestCase {
  @Override
  @NotNull
  public LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }

  public void testSkipCompiledClassesWhileCheckingIfToIncludeSyntheticMembers() {
    GrAccessorMethod resolved = resolveByText("""
                                          
                                              class MyBase {}
                                              trait MyT {}
                                              class MyInheritor extends Script implements MyT {
                                                def ppp
                                              }
                                              new MyInheritor().pp<caret>p
                                              """, GrAccessorMethod.class);
    Assert.assertEquals("getPpp", resolved.getName());
  }

  public void testTransformAnonymousClasses() {
    myFixture.addFileToProject("Base.groovy", """
        abstract class Base {
          abstract getFoo()
        }
        """);
    myFixture.configureByText("a.groovy", """
        class A {
          def baz = new Base() { // no error, getFoo() exists
            def foo = 1
          }
        }
        """);
    myFixture.checkHighlighting();
  }
}
