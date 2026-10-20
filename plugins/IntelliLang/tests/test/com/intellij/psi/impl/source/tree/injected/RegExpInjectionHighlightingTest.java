// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class RegExpInjectionHighlightingTest extends LightQuickFixTestCase {
  private static final String BASE_PATH = "/injected";

  @Override
  public @NotNull String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/plugins/IntelliLang/tests/testData";
  }
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ModuleRootModificationUtil.updateModel(getModule(), DefaultLightProjectDescriptor::addJetBrainsAnnotations);
    new MyTestInjector(getPsiManager()).injectAll(getTestRootDisposable());
  }

  private @NotNull String getFilePath() {
    List<File> files = FileUtil.findFilesByMask(Pattern.compile(getTestName(false) + "\\..*"), new File(getTestDataPath() + BASE_PATH));
    File file = assertOneElement(files);
    return StringUtil.trimStart(file.getPath(), getTestDataPath());
  }

  @Override
  protected @NotNull List<HighlightInfo> doHighlighting() {
    List<HighlightInfo> infos = new ArrayList<>(super.doHighlighting());
    if (getTestName(false).contains("SyntaxOnly")) {
      boolean injectionFound = removeNotRelevantInfosForSyntaxOnly(infos);
      assertTrue("Injected fragment not found", injectionFound);
    }
    if (getTestName(false).contains("NotEmptyDescription")) {
      infos.removeIf(info -> info.getDescription() == null);
    }
    return infos;
  }

  private static boolean removeNotRelevantInfosForSyntaxOnly(@NotNull List<HighlightInfo> infos) {
    boolean injectionFound = false;
    for (int i = infos.size() - 1; i >= 0; i--) {
      HighlightInfo info = infos.get(i);
      if (info.type == HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT) {
        injectionFound = true;
        if (info.getToolTip() != null) {
          infos.remove(i); // whole injected fragment
        }
      }
      else if (info.getSeverity() != HighlightInfoType.SYMBOL_TYPE_SEVERITY || info.getDescription() == null){ //description is not null for todo
        infos.remove(i);
      }
    }
    return injectionFound;
  }

  public void testRegexpInjectsToAllOperandsCorrectly() {
    @Language("JAVA")
    String text = "class S {void f(String str) { str.replaceAll(<caret>2 + '<' + \"/>\", \"\");} }";
    configureFromFileText("a.java", text);
    type(" ");
    backspace();
    doHighlighting();
    int offset = getFile().getText().indexOf("/>");
    InjectedLanguageManager
      .getInstance(getProject()).enumerate(getFile().findElementAt(offset), (injectedPsi, places) -> assertEquals("2</>", injectedPsi.getText()));
  }

  public void testImproperRegexp() {
    configureByFile(getFilePath());
    assertEmpty(doHighlighting(HighlightSeverity.ERROR));
  }
}
