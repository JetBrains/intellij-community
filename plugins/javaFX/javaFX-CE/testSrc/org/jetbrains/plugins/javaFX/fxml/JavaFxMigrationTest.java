package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.refactoring.migration.MigrationMap;
import com.intellij.refactoring.migration.MigrationMapSet;
import com.intellij.refactoring.migration.MigrationProcessor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxMigrationTest extends LightCodeInsightFixtureTestCase {

  public void testImportClasses() {
    doTest();
  }

  public void testImportSkinPackage() {
    doTest();
  }

  public void testImportCssPackage() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    MigrationMap[] maps = new MigrationMapSet().getMaps();
    MigrationMap migrationMap = ContainerUtil.find(maps, m -> "JavaFX (8 -> 9)".equals(m.getName()));
    assertNotNull(migrationMap);

    new MigrationProcessor(getProject(), migrationMap).run();
    FileDocumentManager.getInstance().saveAllDocuments();
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/migration/";
  }
}
