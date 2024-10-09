// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.logback;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.plugins.groovy.LibraryLightProjectDescriptor;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.RepositoryTestLibrary;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.jetbrains.plugins.groovy.GroovyProjectDescriptors.LIB_GROOVY_LATEST;

public class LogbackTest extends LightGroovyTestCase {
  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LOGBACK;
  }

  private static final RepositoryTestLibrary LIB_LOGBACK =
    new RepositoryTestLibrary("ch.qos.logback:logback-classic:1.2.3", DependencyScope.RUNTIME);
  private static final LightProjectDescriptor LOGBACK = new LibraryLightProjectDescriptor(LIB_GROOVY_LATEST.plus(LIB_LOGBACK)) {
    @Override
    public @NotNull JavaResourceRootType getSourceRootType() {
      return JavaResourceRootType.RESOURCE;
    }
  };

  public void testHighlighting() {
    myFixture.configureByText("logback.groovy", """
      appender("FULL_STACKTRACE", FileAppender) {
          file = "./stacktrace.log"
          append = true
          <warning descr="Cannot resolve symbol 'setAppend'">setAppend</warning>(true)
          encoder(PatternLayoutEncoder) {
              pattern = "%level %logger - %msg%n"
          }
          <warning descr="Cannot resolve symbol 'encoder'">encoder</warning>
          <warning descr="Cannot resolve symbol 'setEncoder'">setEncoder</warning>(new EchoEncoder())
      }
      
      logger("StackTrace", ERROR, ['FULL_STACKTRACE'], false)
      root(warn, [''])
      """);
    myFixture.enableInspections(GrUnresolvedAccessInspection.class);
    myFixture.checkHighlighting();
  }

  public void testComponentDelegateCompletion() {
    JavaCodeInsightTestFixture fixture = getFixture();
    myFixture.configureByText("logback.groovy", """
      appender("FULL_STACKTRACE", FileAppender) {
          <caret>
      }
      
      logger("StackTrace", ERROR, ['FULL_STACKTRACE'], false)
      """);
    myFixture.completeBasic();
    List<String> lookupElementStrings = fixture.getLookupElementStrings();
    Assert.assertTrue(ContainerUtil.all(List.of("file", "append", "encoder"), it -> lookupElementStrings.contains(it)));
  }

  public void testTargetCompletion() {
    myFixture.configureByText("logback.groovy", """
      appender('FOO_APP', ConsoleAppender)
      appender("BAR_APP", FileAppender) {}
      logger("", ERROR, ['<caret>'])
      """);
    myFixture.completeBasic();
    assert DefaultGroovyMethods.equals(DefaultGroovyMethods.toSet(new ArrayList<>(Arrays.asList("FOO_APP", "BAR_APP"))),
                                       DefaultGroovyMethods.toSet(getFixture().getLookupElementStrings()));
  }

  public void testTargetNavigation() {
    myFixture.configureByText("logback.groovy", """
      appender('FOO_APP', ConsoleAppender)
      appender("BAR_APP", FileAppender) {}
      logger("", ERROR, ['FOO<caret>_APP'])
      """);
    myFixture.performEditorAction("GotoDeclaration");
    myFixture.checkResult("""
                  appender('<caret>FOO_APP', ConsoleAppender)
                  appender("BAR_APP", FileAppender) {}
                  logger("", ERROR, ['FOO_APP'])
                  """);
  }

  public void testElementToFindUsagesOfExists() {
    myFixture.configureByText("logback.groovy", """
      appender('FOO_<caret>APP', ConsoleAppender)
      """);
    PomTargetPsiElement targetElement = (PomTargetPsiElement) GotoDeclarationAction.findElementToShowUsagesOf(getEditor(), myFixture.getCaretOffset());

    Assert.assertNotNull(targetElement);
    Assert.assertTrue(targetElement.getTarget() instanceof AppenderTarget);
  }

  public void testNoErrorWhenClassWithSameNameExists() {
    myFixture.addClass("""
               package pckg1;
               public class SomeClass {}
               """);
    myFixture.addClass("""
               
               package pckg2;
               public class SomeConfigurableClass {
                 public void setSomeClass(pckg1.SomeClass someClass) {}
               }""");
    myFixture.configureByText("logback.groovy", """
      appender('foo', pckg2.SomeConfigurableClass) {
        someClass = SomeClass.<caret>
      }
      """);
    myFixture.enableInspections(GrUnresolvedAccessInspection.class);
    myFixture.completeBasic();
    }
}
