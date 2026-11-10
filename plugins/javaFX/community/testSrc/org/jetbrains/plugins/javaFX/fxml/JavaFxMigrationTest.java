// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.refactoring.migration.MigrationMap;
import com.intellij.refactoring.migration.MigrationMapSet;
import com.intellij.refactoring.migration.MigrationProcessor;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class JavaFxMigrationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(PluginPathManager.getPluginHomePath("javaFX") + "/testData/migration/");
  }

  @Test
  public void testImportClasses() {
    doTest();
  }

  @Test
  public void testImportSkinPackage() {
    doTest();
  }

  @Test
  public void testImportCssPackage() {
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    MigrationMap[] maps = new MigrationMapSet().getMaps();
    MigrationMap migrationMap = ContainerUtil.find(maps, m -> "JavaFX (8 -> 9)".equals(m.getName()));
    assertNotNull(migrationMap);

    EdtTestUtil.runInEdtAndWait(() -> {
      new MigrationProcessor(myFixture.getProject(), migrationMap).run();
      FileDocumentManager.getInstance().saveAllDocuments();
      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }
}
