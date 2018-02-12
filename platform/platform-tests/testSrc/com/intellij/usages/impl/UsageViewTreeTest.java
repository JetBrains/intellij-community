/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.usages.impl;

import com.intellij.module.ModuleGroupTestsKt;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.TreeTester;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.xmlb.XmlSerializerUtil;

import java.io.File;

/**
 * @author nik
 */
public class UsageViewTreeTest extends UsefulTestCase {
  private TestFixtureBuilder<IdeaProjectTestFixture> myFixtureBuilder;
  private CodeInsightTestFixture myFixture;
  private Disposable myDisposable;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixtureBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder("moduleGroups");
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myFixtureBuilder.getFixture());
    myDisposable = () -> {
      try {
        myFixture.tearDown();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    };
    myFixture.setUp();
    disposeOnTearDown(myDisposable);
    UsageViewSettings oldSettingsState = new UsageViewSettings();
    UsageViewSettings settings = UsageViewSettings.getInstance();
    XmlSerializerUtil.copyBean(settings.getState(), oldSettingsState);
    disposeOnTearDown(() -> settings.loadState(oldSettingsState));

    settings.setGroupByFileStructure(false);
    settings.setGroupByUsageType(false);
    settings.setGroupByPackage(false);
  }

  public void testSimpleModule() throws Exception {
    addModule("main");
    PsiFile file = myFixture.addFileToProject("main/A.txt", "hello");
    Usage[] usages = {new UsageInfo2UsageAdapter(new UsageInfo(file))};
    assertUsageViewStructureEquals(usages, "Usage (1 usage)\n" +
                                           " Non-code usages (1 usage)\n" +
                                           "  main (1 usage)\n" +
                                           "   A.txt (1 usage)\n" +
                                           "    1hello\n");
  }

  public void testModuleWithQualifiedName() throws Exception {
    addModule("xxx.main");
    PsiFile file = myFixture.addFileToProject("xxx.main/A.txt", "hello");
    Usage[] usages = {new UsageInfo2UsageAdapter(new UsageInfo(file))};
    UsageViewSettings.getInstance().setFlattenModules(false);
    ModuleGroupTestsKt.runWithQualifiedModuleNamesEnabled(() -> {
      assertUsageViewStructureEquals(usages, "Usage (1 usage)\n" +
                                             " Non-code usages (1 usage)\n" +
                                             "  xxx (1 usage)\n" +
                                             "   main (1 usage)\n" +
                                             "    A.txt (1 usage)\n" +
                                             "     1hello\n");
      return null;
    });
  }

  private void assertUsageViewStructureEquals(Usage[] usages, String expected) {
    UsageViewImpl usageView = (UsageViewImpl)UsageViewManager
      .getInstance(myFixture.getProject()).createUsageView(UsageTarget.EMPTY_ARRAY, usages, new UsageViewPresentation(), null);
    Disposer.register(myDisposable, usageView);
    usageView.expandAll();
    TreeTester.forNode(usageView.getRoot()).withPresenter(usageView::getNodeText).assertStructureEquals(expected);
  }

  private void addModule(String name) throws Exception {
    String tempDirPath = myFixture.getTempDirPath();
    EmptyModuleFixtureBuilder moduleBuilder = myFixtureBuilder.addModule(EmptyModuleFixtureBuilder.class);
    String sourceRoot = tempDirPath + "/" + name;
    FileUtil.createDirectory(new File(sourceRoot));
    moduleBuilder.addSourceContentRoot(sourceRoot);
    moduleBuilder.getFixture().setUp();
    ModuleGroupTestsKt.renameModule(myFixture.getModule(), name);
  }
}
