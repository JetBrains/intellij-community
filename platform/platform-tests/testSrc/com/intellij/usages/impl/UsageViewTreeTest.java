/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.usages.impl;

import com.intellij.module.ModuleGroupTestsKt;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
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
    myDisposable = new Disposable() {
      @Override
      public void dispose() {
        try {
          myFixture.tearDown();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };
    myFixture.setUp();
    disposeOnTearDown(myDisposable);
    UsageViewSettings oldSettings = new UsageViewSettings();
    XmlSerializerUtil.copyBean(UsageViewSettings.getInstance().getState(), oldSettings);
    disposeOnTearDown(() -> UsageViewSettings.getInstance().loadState(oldSettings));
  }

  public void testSingleModule() throws Exception {
    String tempDirPath = myFixture.getTempDirPath();
    EmptyModuleFixtureBuilder moduleBuilder = myFixtureBuilder.addModule(EmptyModuleFixtureBuilder.class);
    moduleBuilder.addSourceContentRoot(tempDirPath);
    moduleBuilder.getFixture().setUp();
    ModuleGroupTestsKt.renameModule(myFixture.getModule(), "main");
    PsiFile file = myFixture.addFileToProject("A.txt", "hello");
    Usage[] usages = new Usage[] {new UsageInfo2UsageAdapter(new UsageInfo(file))};
    UsageViewSettings settings = UsageViewSettings.getInstance();
    settings.GROUP_BY_FILE_STRUCTURE = false;
    settings.GROUP_BY_USAGE_TYPE = false;
    settings.GROUP_BY_PACKAGE = false;
    UsageViewImpl usageView = (UsageViewImpl)UsageViewManager.getInstance(myFixture.getProject()).createUsageView(UsageTarget.EMPTY_ARRAY, usages, new UsageViewPresentation(), null);
    Disposer.register(myDisposable, usageView);
    usageView.expandAll();
    TreeTester.forNode(usageView.getRoot()).withPresenter(usageView::getNodeText).assertStructureEquals("Usage (1 usage)\n" +
                                                                                                        " Non-code usages (1 usage)\n" +
                                                                                                        "  main (1 usage)\n" +
                                                                                                        "   A.txt (1 usage)\n" +
                                                                                                        "    1hello\n");
  }
}
