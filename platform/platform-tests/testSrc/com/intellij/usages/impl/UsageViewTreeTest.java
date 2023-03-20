// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.module.ModuleGroupTestsKt;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewSettings;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

public class UsageViewTreeTest extends UsefulTestCase {
  private TestFixtureBuilder<IdeaProjectTestFixture> myFixtureBuilder;
  private CodeInsightTestFixture myFixture;
  private UsageView usageView;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixtureBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder("moduleGroups");
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myFixtureBuilder.getFixture());
    myFixture.setUp();
    disposeOnTearDown(() -> {
      try {
        if (usageView != null) {
          waitForUsages(usageView);
        }
        myFixture.tearDown();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
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
    assertUsageViewStructureEquals(new UsageInfo(file), """
      <root> (1)
       Non-code usages in (1)
        main (1)
         A.txt (1)
          1hello
      """);
  }

  public void testModuleWithQualifiedName() throws Exception {
    addModule("xxx.main");
    PsiFile file = myFixture.addFileToProject("xxx.main/A.txt", "hello");
    UsageViewSettings.getInstance().setFlattenModules(false);
    ModuleGroupTestsKt.runWithQualifiedModuleNamesEnabled(() -> {
      assertUsageViewStructureEquals(new UsageInfo(file), """
        <root> (1)
         Non-code usages in (1)
          xxx (1)
           main (1)
            A.txt (1)
             1hello
        """);
      return null;
    });
  }

  public void testGroupByDirectoryStructureMustMaintainNestedDirectories() throws Exception {
    addModule("xxx.main");
    UsageViewSettings.getInstance().setGroupByPackage(true);
    UsageViewSettings.getInstance().setGroupByDirectoryStructure(true); // must ignore group by package
    PsiFile file = myFixture.addFileToProject("xxx.main/x/i1/A.txt", "hello");
    PsiFile file2 = myFixture.addFileToProject("xxx.main/y/B.txt", "hello");
    assertEquals("""
                   <root> (2)
                    Non-code usages in (2)
                     xxx.main (2)
                      x (1)
                       i1 (1)
                        A.txt (1)
                         1hello
                      y (1)
                       B.txt (1)
                        1hello
                   """
      , myFixture.getUsageViewTreeTextRepresentation(Arrays.asList(new UsageInfo(file), new UsageInfo(file2))));
  }


  private void assertUsageViewStructureEquals(@NotNull UsageInfo usage, String expected) {
    assertEquals(expected, myFixture.getUsageViewTreeTextRepresentation(Collections.singleton(usage)));
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

  private void waitForUsages(UsageView usageView) {
    if (usageView instanceof UsageViewImpl) {
      ProgressManager.getInstance().run(new Task.Modal(getProject(), "Waiting", false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          ((UsageViewImpl)usageView).waitForUpdateRequestsCompletion();
          ((UsageViewImpl)usageView).drainQueuedUsageNodes();
          ((UsageViewImpl)usageView).searchFinished();
        }
      });
    }
  }

  protected Project getProject() {
    return myFixture.getProject();
  }
}
