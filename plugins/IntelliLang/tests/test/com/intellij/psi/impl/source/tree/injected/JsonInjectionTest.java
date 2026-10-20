// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.json.JsonBundle;
import com.intellij.json.codeinsight.JsonStandardComplianceInspection;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Checks that the JSON compliance inspection accepts a JSON fragment injected into a Java string.
 */
public class JsonInjectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/plugins/IntelliLang/tests/testData/injected/";
  }

  // PY-133425
  public void testJsonFragmentInjectedInJavaString() {
    myFixture.enableInspections(new JsonStandardComplianceInspection());
    myFixture.configureByFile(getTestName(false) + ".java");
    List<HighlightInfo> highlightInfos = myFixture.doHighlighting();
    String message = JsonBundle.message("inspection.compliance.msg.illegal.property.key");
    assertNull(ContainerUtil.find(highlightInfos, info -> message.equals(info.getDescription())));
  }
}
