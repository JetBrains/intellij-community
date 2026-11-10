// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.codeInsight;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class PluginXmlIdentifierHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setReadEditorMarkupModel(true);
  }

  @Test
  public void test_ep_references() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      IdentifierHighlighterPassFactory.doWithIdentifierHighlightingEnabled(myFixture.getProject(), () -> {

        myFixture.configureByText("plugin.xml", """
  <idea-plugin>
    <extensionPoints>
      <extensionPoint name="foo<caret>.bar"/>
    </extensionPoints>
    <extensions>
      <foo.bar/>
    </extensions>
  </idea-plugin>
  """);
        List<HighlightInfo> infos = myFixture.doHighlighting();
        assertEquals(2, infos.stream().filter(it ->
          it.getSeverity() == HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY
        ).count());
      });
    });
  }
}
