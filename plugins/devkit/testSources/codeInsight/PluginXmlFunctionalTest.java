/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.idea.devkit.codeInsight;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import org.jetbrains.idea.devkit.DevKitInspectionToolProvider;

import java.io.IOException;

/**
 * @author peter
 */
public class PluginXmlFunctionalTest extends CodeInsightFixtureTestCase {
  private TempDirTestFixture myTempDirFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTempDirFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();
    myFixture.enableInspections(new DevKitInspectionToolProvider());
  }

  @Override
  protected String getBasePath() {
    return "/svnPlugins/devkit/testData/codeInsight";
  }

  public void testExtensionsHighlighting() throws Throwable {
    final String root = "idea_core";
    addPluginXml(root, "<idea-plugin>\n" +
                       "    <id>com.intellij</id>\n" +
                       "    <extensionPoints>\n" +
                       "        <extensionPoint name=\"completion.contributor\"/>\n" +
                       "    </extensionPoints>\n" +
                       "</idea-plugin>");
    addPluginXml("custom", "<idea-plugin>\n" +
                           "    <id>com.intellij.custom</id>\n" +
                           "    <extensionPoints>\n" +
                           "        <extensionPoint name=\"custom\"/>\n" +
                           "    </extensionPoints>\n" +
                           "</idea-plugin>");

    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".xml", "META-INF/plugin.xml"));
    myFixture.checkHighlighting(false, false, false);
  }

  public void testDependsHighlighting() throws Throwable {
    final String root = "idea_core";
    addPluginXml(root, "<idea-plugin>\n" +
                       "    <id>com.intellij</id>\n" +
                       "</idea-plugin>");
    addPluginXml("custom", "<idea-plugin>\n" +
                           "    <id>com.intellij.custom</id>\n" +
                           "</idea-plugin>");

    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".xml", "META-INF/plugin.xml"));
    myFixture.checkHighlighting(false, false, false);
  }

  private void addPluginXml(final String root, final String text) throws IOException {
    myTempDirFixture.createFile(root +
                                "/META-INF/plugin.xml", text);
    new WriteCommandAction(getProject()) {
      protected void run(Result result) throws Throwable {
        PsiTestUtil.addSourceContentToRoots(myModule, myTempDirFixture.getFile(root));
      }
    }.execute();
  }

}
