// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerOptions;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class RegExpInjectionHighlightingTest {
  private static final String BASE_PATH = "/injected";
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture =
    lightDaemonAnalyzerFixture(projectFixture, List::of, LightDaemonAnalyzerOptions.DEFAULT.withBasePath("/plugins/IntelliLang/tests/testData"));

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    ModuleRootModificationUtil.updateModel(myFixture.getModule(), DefaultLightProjectDescriptor::addJetBrainsAnnotations);
    new MyTestInjector(PsiManager.getInstance(myFixture.getProject())).injectAll(myFixture.getTestRootDisposable());
  }

  private @NotNull String getFilePath() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    List<File> files = FileUtil.findFilesByMask(Pattern.compile(myFixture.getTestName(false) + "\\..*"), new File(myFixture.getTestDataPath() + BASE_PATH));
    assertEquals(1, files.size(), files::toString);
    return BASE_PATH + "/" + files.getFirst().getName();
  }

  @Test
  public void testRegexpInjectsToAllOperandsCorrectly() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    @Language("JAVA")
    String text = "class S {void f(String str) { str.replaceAll(<caret>2 + '<' + \"/>\", \"\");} }";
    daemon.configureFromFileText("a.java", text);
    daemon.type(" ");
    daemon.backspace();
    daemon.doHighlighting();
    ReadAction.runBlocking(() -> {
      PsiFile file = myFixture.getFile();
      int offset = file.getText().indexOf("/>");
      InjectedLanguageManager
        .getInstance(myFixture.getProject()).enumerate(file.findElementAt(offset), (injectedPsi, places) -> assertEquals("2</>", injectedPsi.getText()));
    });
  }

  @Test
  public void testImproperRegexp() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.configureByFile(getFilePath());
    List<HighlightInfo> errors = daemon.doHighlighting(HighlightSeverity.ERROR);
    assertTrue(errors.isEmpty(), errors::toString);
  }

  @Test
  public void testInjectedSyntaxFollowsTypingInsideFragment() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.configureFromFileText("a.java", "class S {void f(String str) { str.replaceAll(\"a<caret>+b\", \"\");} }");
    assertFalse(injectedSyntaxRanges().isEmpty());
    daemon.type("cd");
    List<String> afterTyping = injectedSyntaxRanges();

    daemon.configureFromFileText("a.java", "class S {void f(String str) { str.replaceAll(\"acd+b\", \"\");} }");
    List<String> fresh = injectedSyntaxRanges();
    assertTrue(fresh.stream().anyMatch(range -> range.endsWith(":d")), fresh.toString());
    assertEquals(fresh, afterTyping);
  }

  /** Lexer token ranges of the injected fragments as {@code "start..end:text"}, sorted by offset. */
  private @NotNull List<String> injectedSyntaxRanges() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    String text = ReadAction.computeBlocking(
      () -> InjectedLanguageEditorUtil.getTopLevelEditor(daemon.getFixture().getEditor()).getDocument().getText());
    return daemon.doHighlighting().stream()
      .filter(info -> info.type == HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT &&
                      info.getToolId() == InjectedLanguageManagerImpl.INJECTION_SYNTAX_TOOL_ID)
      .map(info -> TextRange.create(info.getStartOffset(), info.getEndOffset()))
      .distinct()
      .sorted(Comparator.comparingInt(TextRange::getStartOffset).thenComparingInt(TextRange::getEndOffset))
      .map(range -> range.getStartOffset() + ".." + range.getEndOffset() + ":" + range.substring(text))
      .toList();
  }
}
