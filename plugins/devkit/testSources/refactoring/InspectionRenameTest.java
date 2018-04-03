/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.idea.devkit.refactoring;

import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.components.JBList;
import com.intellij.util.PathUtil;
import com.intellij.util.xmlb.annotations.Attribute;

import java.nio.file.Paths;

@TestDataPath("$CONTENT_ROOT/testData/refactoring/renameInspection")
public class InspectionRenameTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/refactoring/renameInspection";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    super.tuneFixture(moduleBuilder);

    moduleBuilder.addLibrary("core-api", PathUtil.getJarPathForClass(LanguageExtensionPoint.class));
    moduleBuilder.addLibrary("analysis-api", PathUtil.getJarPathForClass(LocalInspectionEP.class));
    moduleBuilder.addLibrary("platform-resources", Paths.get(PathUtil.getJarPathForClass(LocalInspectionEP.class))
      .resolveSibling("platform-resources").toString());
    moduleBuilder.addLibrary("platform-api", PathUtil.getJarPathForClass(JBList.class));
    moduleBuilder.addLibrary("util", PathUtil.getJarPathForClass(Attribute.class));
  }

  public void testRenameInspectionWithoutGetShortName() {
    doTestRenameInspection("MyInspectionWithoutGetShortName", "/withoutGetShortName/", "NewMyInspectionWithoutGetShortName.html");
  }

  public void testRenameInspectionWithGetShortName() {
    doTestRenameInspection("MyInspectionWithGetShortName", "/withGetShortName/", "someSpecificShortName.html");
  }

  public void testRenameNonStandardNamedInspection() {
    doTestRenameInspection("MyInspectionWithSpecificName", "MyInspectionWithSpecificName", "/nonStandardNamed/", "someShortName.html");
  }


  private void doTestRenameInspection(String name, String testDataSubPath, String expectedDescriptionFileName) {
    doTestRenameInspection(name, name + "Inspection", testDataSubPath, expectedDescriptionFileName);
  }

  private void doTestRenameInspection(String name, String inspectionName, String testDataSubPath,
                                      String expectedDescriptionFileName) {
    myFixture.configureByFile(testDataSubPath + name + ".xml");
    myFixture.copyFileToProject(testDataSubPath + inspectionName + ".java");
    myFixture.copyDirectoryToProject(testDataSubPath + "inspectionDescriptions", "inspectionDescriptions");

    JavaPsiFacadeEx javaFacade = myFixture.getJavaFacade();
    PsiClass inspectionClass = javaFacade.findClass(inspectionName);
    assertNotNull(inspectionName, inspectionClass);

    RenameProcessor processor = new RenameProcessor(getProject(), inspectionClass, "New" + inspectionName, true, true);
    for (AutomaticRenamerFactory factory : Extensions.getExtensions(AutomaticRenamerFactory.EP_NAME)) {
      processor.addRenamerFactory(factory);
    }
    processor.run();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();

    myFixture.checkResultByFile(testDataSubPath + name + ".xml", testDataSubPath + name + "_after.xml", false);
    VirtualFile descriptionFile = myFixture.findFileInTempDir("inspectionDescriptions/" + expectedDescriptionFileName);
    assertNotNull(expectedDescriptionFileName, descriptionFile);
  }
}
