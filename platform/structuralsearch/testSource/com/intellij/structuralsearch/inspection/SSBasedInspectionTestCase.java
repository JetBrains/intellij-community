// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import org.jetbrains.annotations.NotNull;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_1_7;

/**
 * @author Bas Leijdekkers
 */
public abstract class SSBasedInspectionTestCase extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;
  private SSBasedInspection myInspection;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder =
      factory.createLightFixtureBuilder(JAVA_1_7, getTestName(false));
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, new LightTempDirTestFixtureImpl(true));
    myInspection = new SSBasedInspection();
    myFixture.setUp();
    myFixture.enableInspections(myInspection);
    myFixture.setTestDataPath(getTestDataPath());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myFixture = null;
      myInspection = null;
      super.tearDown();
    }
  }

  protected void doTest(JavaFileType fileType, String searchPattern, String patternName) {
    doTest(fileType, searchPattern, patternName, null);
  }

  protected void doTest(JavaFileType fileType, String search, String name, String replacement) {
    final Configuration configuration = replacement == null ? new SearchConfiguration() : new ReplaceConfiguration();
    configuration.setName(name);

    final MatchOptions matchOptions = configuration.getMatchOptions();
    matchOptions.setFileType(fileType);
    matchOptions.fillSearchCriteria(search);
    if (replacement != null) {
      configuration.getReplaceOptions().setReplacement(replacement);
    }

    StructuralSearchProfileActionProvider.createNewInspection(configuration, myFixture.getProject());
    myFixture.testHighlighting(true, false, false, getTestName(false) + getExtension());
    if (replacement != null) {
      final IntentionAction intention = myFixture.getAvailableIntention(CommonQuickFixBundle.message("fix.replace.with.x", replacement));
      assertNotNull(intention);
      myFixture.checkPreviewAndLaunchAction(intention);
      myFixture.checkResultByFile(getTestName(false) + ".after" + getExtension());
    }
  }

  @NotNull
  protected abstract String getExtension();

  protected abstract String getTestDataPath();
}
