// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class DelegatesToSupportTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(GrUnresolvedAccessInspection.class, GroovyAssignabilityCheckInspection.class);
  }

  @Test
  public void testExtensionMethodGenericTypeIndex() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testExtensionMethodGenericType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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