/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.usages.impl;

import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.module.ModuleGroupTestsKt;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

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
    assertUsageViewStructureEquals(new UsageInfo(file), "Usage (1 usage)\n" +
                                                        " Non-code usages (1 usage)\n" +
                                                        "  main (1 usage)\n" +
                                                        "   A.txt (1 usage)\n" +
                                                        "    1hello\n");
  }

  public void testModuleWithQualifiedName() throws Exception {
    addModule("xxx.main");
    PsiFile file = myFixture.addFileToProject("xxx.main/A.txt", "hello");
    UsageViewSettings.getInstance().setFlattenModules(false);
    ModuleGroupTestsKt.runWithQualifiedModuleNamesEnabled(() -> {
      assertUsageViewStructureEquals(new UsageInfo(file), "Usage (1 usage)\n" +
                                                          " Non-code usages (1 usage)\n" +
                                                          "  xxx (1 usage)\n" +
                                                          "   main (1 usage)\n" +
                                                          "    A.txt (1 usage)\n" +
                                                          "     1hello\n");
      return null;
    });
  }

  public void testGroupByDirectoryStructureMustMaintainNestedDirectories() throws Exception {
    addModule("xxx.main");
    UsageViewSettings.getInstance().setGroupByModule(false);
    UsageViewSettings.getInstance().setGroupByPackage(true);
    UsageViewSettings.getInstance().setGroupByDirectoryStructure(true); // must ignore group by package
    PsiFile file = myFixture.addFileToProject("xxx.main/x/i1/A.txt", "hello");
    PsiFile file2 = myFixture.addFileToProject("xxx.main/y/B.txt", "hello");

    usageView = createUsageViewImpl(Arrays.asList(new UsageInfo(file), new UsageInfo(file2)));

    if (myFixture instanceof CodeInsightTestFixtureImpl && usageView instanceof UsageViewImpl) {
      assertEquals("Usage (2 usages)\n" +
                   " Non-code usages (2 usages)\n" +
                   "  xxx.main (2 usages)\n" +
                   "   x/i1 (1 usage)\n" +
                   "    A.txt (1 usage)\n" +
                   "     1hello\n" +
                   "   y (1 usage)\n" +
                   "    B.txt (1 usage)\n" +
                   "     1hello\n"
        , ((CodeInsightTestFixtureImpl)myFixture).getUsageViewTreeTextRepresentation((UsageViewImpl)usageView));
    }
    else {
      assertEquals("Usage (2 usages)\n" +
                   " Non-code usages (2 usages)\n" +
                   "  xxx.main (2 usages)\n" +
                   "   x/i1 (1 usage)\n" +
                   "    A.txt (1 usage)\n" +
                   "     1hello\n" +
                   "   y (1 usage)\n" +
                   "    B.txt (1 usage)\n" +
                   "     1hello\n"
        , myFixture.getUsageViewTreeTextRepresentation(Arrays.asList(new UsageInfo(file), new UsageInfo(file2))));
    }
  }

  public void testGroupByDirectoryStructureFirstPathsCompactedThenSplitted() throws Exception {
    addModule("xxx.main");
    UsageViewSettings.getInstance().setGroupByModule(false);
    UsageViewSettings.getInstance().setGroupByPackage(true);
    UsageViewSettings.getInstance().setGroupByDirectoryStructure(true); // must ignore group by package
    PsiFile file = myFixture.addFileToProject("xxx.main/x/x1/x2/A.txt", "hello");

    usageView = createUsageViewImpl(Arrays.asList(new UsageInfo(file)));
    if (myFixture instanceof CodeInsightTestFixtureImpl && usageView instanceof UsageViewImpl) {
      assertEquals("Usage (1 usage)\n" +
                   " Non-code usages (1 usage)\n" +
                   "  xxx.main/x/x1/x2 (1 usage)\n" +
                   "   A.txt (1 usage)\n" +
                   "    1hello\n"
        , ((CodeInsightTestFixtureImpl)myFixture).getUsageViewTreeTextRepresentation((UsageViewImpl)usageView));
    }
    else {
      assertEquals("Usage (1 usage)\n" +
                   " Non-code usages (1 usage)\n" +
                   "  xxx.main/x/x1/x2 (1 usage)\n" +
                   "   A.txt (1 usage)\n" +
                   "    1hello\n"
        , myFixture.getUsageViewTreeTextRepresentation(Arrays.asList(new UsageInfo(file))));
    }

    PsiFile file2 = myFixture.addFileToProject("xxx.main/x/A1.txt", "hello");
    usageView = createUsageViewImpl(Arrays.asList(new UsageInfo(file), new UsageInfo(file2)));
    if (myFixture instanceof CodeInsightTestFixtureImpl && usageView instanceof UsageViewImpl) {
      assertEquals("Usage (2 usages)\n" +
                   " Non-code usages (2 usages)\n" +
                   "  xxx.main/x (2 usages)\n" +
                   "   x1/x2 (1 usage)\n" +
                   "    A.txt (1 usage)\n" +
                   "     1hello\n" +
                   "   A1.txt (1 usage)\n" +
                   "    1hello\n"
        , ((CodeInsightTestFixtureImpl)myFixture).getUsageViewTreeTextRepresentation((UsageViewImpl)usageView));
    }
    else {
      assertEquals("Usage (2 usages)\n" +
                   " Non-code usages (2 usages)\n" +
                   "  xxx.main/x (2 usages)\n" +
                   "   x1/x2 (1 usage)\n" +
                   "    A.txt (1 usage)\n" +
                   "     1hello\n" +
                   "   A1.txt (1 usage)\n" +
                   "    1hello\n"
        , myFixture.getUsageViewTreeTextRepresentation(Arrays.asList(new UsageInfo(file), new UsageInfo(file2))));
    }
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

  private UsageView createUsageViewImpl(@NotNull Collection<? extends UsageInfo> usages) {
    return UsageViewManager
      .getInstance(getProject()).createUsageView(UsageTarget.EMPTY_ARRAY,
                                                 ContainerUtil.map(usages, usage -> new UsageInfo2UsageAdapter(usage))
                                                   .toArray(Usage.EMPTY_ARRAY),
                                                 new UsageViewPresentation(),
                                                 null);
  }
}
