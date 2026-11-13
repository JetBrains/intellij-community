// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.injection;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.JBIterable;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class InjectedGroovyParsingTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy =
    groovyFixture(project, "injected/");

  @BeforeEach
  public void setUp() {
    new MyTestInjector(groovy.get().getPsiManager()).injectAll(groovy.get().getTestRootDisposable());
  }

  @Test
  public void testCommentInjectionInGroovy() {
    JavaCodeInsightTestFixture fixture = groovy.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      fixture.configureByFile(groovy.get().getTestName(false) + ".groovy");
      PlatformTestUtil.assertSameLinesWithFile(fixture.getTestDataPath() + File.separatorChar + groovy.get().getTestName(false) + ".txt",
                                               toParseTreeText(fixture.getFile()));
    });
  }

  private @NotNull String toParseTreeText(@NotNull PsiFile file) {
    return String.join("", JBIterable.from(file.getViewProvider().getAllFiles()).map(
      root -> DebugUtil.psiToString(root, true, false, (psiElement, consumer) ->
        InjectedLanguageManager.getInstance(groovy.get().getProject()).enumerate(psiElement, (injectedPsi, places) -> consumer.consume(injectedPsi)))));
  }
}
