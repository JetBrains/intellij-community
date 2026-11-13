// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.injection;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.junit5.fixture.FixturesKt.testNameFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyProjectFixture;

@TestApplication
public class InjectedGroovyParsingTest {
  private static final TestFixture<Project> project = groovyProjectFixture();
  private final TestFixture<JavaCodeInsightTestFixture> groovy =
    groovyFixture(project, LightJavaCodeInsightFixtureTestCase.JAVA_LATEST, "injected/");
  private final TestFixture<String> testName = testNameFixture(false);

  @BeforeEach
  public void setUp() {
    new MyTestInjector(groovy.get().getPsiManager()).injectAll(groovy.get().getTestRootDisposable());
  }

  @Test
  public void testCommentInjectionInGroovy() {
    JavaCodeInsightTestFixture fixture = groovy.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      fixture.configureByFile(testName.get() + ".groovy");
      ParsingTestCase.doCheckResult(fixture.getTestDataPath(), testName.get() + ".txt", toParseTreeText(fixture.getFile()));
    });
  }

  private @NotNull String toParseTreeText(@NotNull PsiFile file) {
    return String.join("", JBIterable.from(file.getViewProvider().getAllFiles()).map(
      root -> DebugUtil.psiToString(root, true, false, (psiElement, consumer) ->
        InjectedLanguageManager.getInstance(groovy.get().getProject()).enumerate(psiElement, (injectedPsi, places) -> consumer.consume(injectedPsi)))));
  }
}
