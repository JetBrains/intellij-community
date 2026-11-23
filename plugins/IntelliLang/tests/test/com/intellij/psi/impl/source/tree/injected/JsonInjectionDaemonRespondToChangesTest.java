// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.json.codeinsight.JsonStandardComplianceInspection;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.daemonAnalyzerFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class JsonInjectionDaemonRespondToChangesTest {
  private static final Logger LOG = Logger.getInstance(JsonInjectionDaemonRespondToChangesTest.class);

  private final TestFixture<DaemonAnalyzerFixture> daemonFixture =
    daemonAnalyzerFixture(getTestDataPath(), JsonInjectionDaemonRespondToChangesTest::configureLocalInspectionTools);

  private static @NotNull String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/plugins/IntelliLang/tests/testData";
  }

  @BeforeEach
  void setUp() {
    ModuleRootModificationUtil.updateModel(daemonFixture.get().getModule(), DefaultLightProjectDescriptor::addJetBrainsAnnotations);
  }

  private static LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new JsonStandardComplianceInspection()};
  }

  @Test
  public void testChangingInjectionLanguageByTypingInLanguageAnnotationMustClearObsoleteHighlightsFromPreviousLanguageInjection() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    @Language("JAVA")
    String text = """
      class S {
          @org.intellij.lang.annotations.Language("JSON")
          String s = "class C { ddd }";
      }""";
    fixture.configureByText(JavaFileType.INSTANCE, text);
    assertThat(fixture.highlightErrors()).isNotEmpty();

    Editor editor = fixture.getEditor();
    EdtTestUtil.runInEdtAndWait(() -> editor.getCaretModel().moveToOffset(editor.getDocument().getText().indexOf("JSON")));
    fixture.executeAction(IdeActions.ACTION_EDITOR_DELETE);
    fixture.executeAction(IdeActions.ACTION_EDITOR_DELETE);
    fixture.executeAction(IdeActions.ACTION_EDITOR_DELETE);
    fixture.executeAction(IdeActions.ACTION_EDITOR_DELETE);
    fixture.type("TEXT");
    LOG.debug("-------------------");
    PsiFile file = fixture.getFile();
    EdtTestUtil.runInEdtAndWait(() -> {
      while (InjectedLanguageUtil.getCachedInjectedDocuments(file).size() != 1) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      }
    });
    fixture.highlightErrors();
    System.gc();System.gc();System.gc();
    DaemonCodeAnalyzerEx.getInstanceEx(fixture.getProject()).restart(fixture.getTestName(false));
    assertThat(fixture.highlightErrors()).isEmpty();
  }
}
