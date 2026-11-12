// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.logback;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.plugins.groovy.LibraryLightProjectDescriptor;
import org.jetbrains.plugins.groovy.RepositoryTestLibrary;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.GroovyProjectDescriptors.LIB_GROOVY_LATEST;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class LogbackTest {

  private static final RepositoryTestLibrary LIB_LOGBACK =
    new RepositoryTestLibrary("ch.qos.logback:logback-classic:1.2.3", DependencyScope.RUNTIME);
  private static final LightProjectDescriptor LOGBACK = new LibraryLightProjectDescriptor(LIB_GROOVY_LATEST.plus(LIB_LOGBACK)) {
    @Override
    public @NotNull JavaResourceRootType getSourceRootType() {
      return JavaResourceRootType.RESOURCE;
    }
  };
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LOGBACK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void testHighlighting() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testComponentDelegateCompletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("logback.groovy", """
      appender("FULL_STACKTRACE", FileAppender) {
          <caret>
      }
      
      logger("StackTrace", ERROR, ['FULL_STACKTRACE'], false)
      """);
    myFixture.completeBasic();
    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertTrue(ContainerUtil.all(List.of("file", "append", "encoder"), it -> lookupElementStrings.contains(it)));
  }

  @Test
  public void testTargetCompletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("logback.groovy", """
      appender('FOO_APP', ConsoleAppender)
      appender("BAR_APP", FileAppender) {}
      logger("", ERROR, ['<caret>'])
      """);
    myFixture.completeBasic();
    assertEquals(Set.of("FOO_APP", "BAR_APP"), new HashSet<>(myFixture.getLookupElementStrings()));
  }

  @Test
  public void testTargetNavigation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testElementToFindUsagesOfExists() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("logback.groovy", """
      appender('FOO_<caret>APP', ConsoleAppender)
      """);
    ReadAction.runBlocking(() -> {
      PomTargetPsiElement targetElement = (PomTargetPsiElement) GotoDeclarationAction.findElementToShowUsagesOf(myFixture.getEditor(), myFixture.getCaretOffset());

      assertNotNull(targetElement);
      assertTrue(targetElement.getTarget() instanceof AppenderTarget);
    });
  }

  @Test
  public void testNoErrorWhenClassWithSameNameExists() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
