// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;

import java.util.List;

public class DelegatesToSupportTest extends LightGroovyTestCase {

  private final LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(GrUnresolvedAccessInspection.class, GroovyAssignabilityCheckInspection.class);
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  public void testExtensionMethodGenericTypeIndex() {
    myFixture.addFileToProject("xt/MyExtensionModule.groovy",
                     """
        package xt;
      
        class MyExtensionModule {
            static <T> void foo(String s, @DelegatesTo.Target Class<T> clazz, @DelegatesTo(genericTypeIndex = 0) Closure c) {}
        }
      """);
    myFixture.addFileToProject("META-INF/services/org.codehaus.groovy.runtime.ExtensionModule",
                     """
        extensionClasses=xt.MyExtensionModule
      """);
    myFixture.configureByText("a.groovy",
                    """
        class MyDelegate {
            def prop
            def bar() {}
        }
      
        "s".foo(MyDelegate) {
            prop
            bar()
            <caret>
        }
      """);
    myFixture.checkHighlighting();
    myFixture.completeBasic();
    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertTrue(lookupElementStrings.contains("prop"));
    assertTrue(lookupElementStrings.contains("bar"));
  }

  public void testExtensionMethodGenericType() {
    myFixture.addClass(
      """
        package test;
      
        import groovy.lang.Closure;
        import groovy.lang.DelegatesTo;
      
        public class TestExtension {
            public static <T> T letIf(T obj, boolean cond, @DelegatesTo(type = "T", strategy = Closure.DELEGATE_FIRST) Closure<T> closure) {}
        }
      """);
    myFixture.addFileToProject("META-INF/services/org.codehaus.groovy.runtime.ExtensionModule", "extensionClasses=test.TestExtension");
    myFixture.configureByText("a.groovy",
      """
      @groovy.transform.CompileStatic
      def foo(String s) {
          s.letIf(true) {
              concat ""
          }
          s.letIf(false) {
              delegate.concat ""
          }
      }
      """);
    myFixture.checkHighlighting();
  }
}