/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.usages.impl;

import com.intellij.module.ModuleGroupTestsKt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageViewSettings;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;

/**
 * @author nik
 */
public class UsageViewTreeTest extends UsefulTestCase {
  private TestFixtureBuilder<IdeaProjectTestFixture> myFixtureBuilder;
  private CodeInsightTestFixture myFixture;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixtureBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder("moduleGroups");
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myFixtureBuilder.getFixture());
    myFixture.setUp();
    disposeOnTearDown(() -> {
      try {
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
}
