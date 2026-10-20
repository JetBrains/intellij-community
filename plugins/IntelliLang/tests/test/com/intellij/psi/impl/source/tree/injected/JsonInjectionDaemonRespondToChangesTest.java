// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.json.codeinsight.JsonStandardComplianceInspection;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.PlatformTestUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public class JsonInjectionDaemonRespondToChangesTest extends DaemonAnalyzerTestCase {
  @Override
  public @NotNull String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/plugins/IntelliLang/tests/testData";
  }
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ModuleRootModificationUtil.updateModel(getModule(), DefaultLightProjectDescriptor::addJetBrainsAnnotations);
  }

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new JsonStandardComplianceInspection()};
  }

  public void testChangingInjectionLanguageByTypingInLanguageAnnotationMustClearObsoleteHighlightsFromPreviousLanguageInjection() {
    @Language("JAVA")
    String text = """
      class S {
          @org.intellij.lang.annotations.Language("JSON")
          String s = "class C { ddd }";
      }""";
    configureByText(JavaFileType.INSTANCE, text);
    assertNotEmpty(highlightErrors());

    getEditor().getCaretModel().moveToOffset(getEditor().getDocument().getText().indexOf("JSON"));
    delete(getEditor());
    delete(getEditor());
    delete(getEditor());
    delete(getEditor());
    type("TEXT");
    LOG.debug("-------------------");
    while (InjectedLanguageUtil.getCachedInjectedDocuments(getFile()).size() != 1) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }
    highlightErrors();
    System.gc();System.gc();System.gc();
    DaemonCodeAnalyzerEx.getInstanceEx(getProject()).restart(getTestName(false));
    assertEmpty(highlightErrors());
  }
}
