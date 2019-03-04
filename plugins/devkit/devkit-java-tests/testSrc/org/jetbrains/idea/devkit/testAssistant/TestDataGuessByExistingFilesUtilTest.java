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
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@TestDataPath("$CONTENT_ROOT/testData/guessByExistingFiles")
public class TestDataGuessByExistingFilesUtilTest extends TestDataPathTestCase {
  private static final String RESOURCES_ROOT_NAME = "resources";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    VirtualFile resourcesDir = ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<VirtualFile, IOException>() {
      @Override
      public VirtualFile compute() throws IOException {
        return myContentRoot.createChildDirectory(TestDataGuessByExistingFilesUtilTest.this, RESOURCES_ROOT_NAME);
      }
    });
    PsiTestUtil.addSourceRoot(myFixture.getModule(), resourcesDir, JavaResourceRootType.RESOURCE);
  }

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "guessByExistingFiles";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("junit3", JavaSdkUtil.getJunit3JarPath());
  }


  public void testGetTestName() {
    String result = TestDataGuessByExistingFilesUtil.getTestName("testTestName");
    assertEquals("TestName", result);
  }

  public void testMoreRelevantFiles() {
    PsiMethod testMethod = getTestMethod("TestMoreRelevant.java",
                                         "Test/testdata_file.txt", "TestMore/testdata_file.txt", "TestMoreRelevant/testdata_file.txt");
    PsiClass testClass = (PsiClass)testMethod.getParent();

    List<TestDataFile> result = TestDataGuessByExistingFilesUtil.suggestTestDataFiles("testdata_file", null, testClass);
    String resultPath = assertOneElement(result).getPath();
    assertTrue(resultPath, resultPath.endsWith("TestMoreRelevant/testdata_file.txt"));
  }

  public void testCollectTestDataByExistingFilesBeforeAndAfter() {
    PsiMethod testMethod = getTestMethodWithBeforeAndAfterTestData();
    List<TestDataFile> result = TestDataGuessByExistingFilesUtil.collectTestDataByExistingFiles(testMethod, null);
    verifyResultForBeforeAndAfter(result);
  }

  public void testSuggestTestDataFilesBeforeAndAfter() {
    PsiMethod testMethod = getTestMethodWithBeforeAndAfterTestData();
    List<TestDataFile> result = TestDataGuessByExistingFilesUtil.suggestTestDataFiles(
      TestDataGuessByExistingFilesUtil.getTestName(testMethod.getName()), null, testMethod.getContainingClass());
    verifyResultForBeforeAndAfter(result);
  }

  public void testCollectTestDataByExistingFilesBeforeAndSame() {
    PsiMethod testMethod = getTestMethodWithBeforeAndSameTestData();
    List<TestDataFile> result = TestDataGuessByExistingFilesUtil.collectTestDataByExistingFiles(testMethod, null);
    verifyResultForBeforeAndSame(result);
  }

  public void testSuggestTestDataFilesBeforeAndSame() {
    PsiMethod testMethod = getTestMethodWithBeforeAndSameTestData();
    List<TestDataFile> result = TestDataGuessByExistingFilesUtil.suggestTestDataFiles(
      TestDataGuessByExistingFilesUtil.getTestName(testMethod.getName()), null, testMethod.getContainingClass());
    verifyResultForBeforeAndSame(result);
  }

  public void testCollectTestDataByExistingFilesAfterAndSame() {
    PsiMethod testMethod = getTestMethodWithAfterAndSameTestData();
    List<TestDataFile> result = TestDataGuessByExistingFilesUtil.collectTestDataByExistingFiles(testMethod, null);
    verifyResultForAfterAndSame(result);
  }

  public void testSuggestTestDataFilesAfterAndSame() {
    PsiMethod testMethod = getTestMethodWithAfterAndSameTestData();
    List<TestDataFile> result = TestDataGuessByExistingFilesUtil.suggestTestDataFiles(
      TestDataGuessByExistingFilesUtil.getTestName(testMethod.getName()), null, testMethod.getContainingClass());
    verifyResultForAfterAndSame(result);
  }

  public void testCollectTestDataByExistingFilesOnlySame() {
    PsiMethod testMethod = getTestMethodWithOnlySameTestData();
    List<TestDataFile> result = TestDataGuessByExistingFilesUtil.collectTestDataByExistingFiles(testMethod, null);
    verifyResultForOnlySame(result);
  }

  public void testSuggestTestDataFilesOnlySame() {
    PsiMethod testMethod = getTestMethodWithOnlySameTestData();
    List<TestDataFile> result = TestDataGuessByExistingFilesUtil.suggestTestDataFiles(
      TestDataGuessByExistingFilesUtil.getTestName(testMethod.getName()), null, testMethod.getContainingClass());
    verifyResultForOnlySame(result);
  }

  public void testCollectTestDataByDirName() {
    PsiMethod testMethod = getTestMethodWithTestDataDir();
    List<TestDataFile> result = TestDataGuessByExistingFilesUtil.suggestTestDataFiles(
      TestDataGuessByExistingFilesUtil.getTestName(testMethod.getName()), null, testMethod.getContainingClass());
    assertSize(2, result);
    assertTrue(result.get(0).exists() && result.get(1).exists());
    assertEquals(ContainerUtil.set("before.java", "after.java"), ContainerUtil.map2Set(result, TestDataFile::getName));
  }

  public void testGuessTestDataByRelatedByDirName() {
    PsiMethod testMethod = getTestMethodWithTestDataDirAndSibling();

    List<TestDataFile> result = TestDataGuessByExistingFilesUtil.guessTestDataName(testMethod);
    assertSize(2, result);
    assertTrue(!result.get(0).exists() && !result.get(1).exists());
    assertEquals(ContainerUtil.set("before.java", "after.java"), ContainerUtil.map2Set(result, TestDataFile::getName));
    String parentName1 = PathUtil.getFileName(PathUtil.getParentPath(result.get(0).getPath()));
    String parentName2 = PathUtil.getFileName(PathUtil.getParentPath(result.get(1).getPath()));

    String expected = StringUtil.decapitalize(TestDataGuessByExistingFilesUtil.getTestName(testMethod.getName()));
    assertEquals(expected, parentName1);
    assertEquals(expected, parentName2);
  }

  private PsiMethod getTestMethodWithBeforeAndAfterTestData() {
    return getTestMethod("SomeTest_BeforeAndAfter.java", "somethingBA_before.java", "somethingBA_after.java");
  }

  private PsiMethod getTestMethodWithBeforeAndSameTestData() {
    return getTestMethod("SomeTest_BeforeAndSame.java", "somethingBS_before.java", "somethingBS.java");
  }

  private PsiMethod getTestMethodWithAfterAndSameTestData() {
    return getTestMethod("SomeTest_AfterAndSame.java", "somethingAS_after.java", "somethingAS.java");
  }

  private PsiMethod getTestMethodWithTestDataDir() {
    return getTestMethod("SomeTestWithTestDataDir.java", "testDataForThisTest/before.java", "testDataForThisTest/after.java");
  }

  private PsiMethod getTestMethodWithTestDataDirAndSibling() {
    return getTestMethod("SomeTestWithTestDataDirAndSibling.java", "testDataForThisTest/before.java", "testDataForThisTest/after.java");
  }

  private PsiMethod getTestMethodWithOnlySameTestData() {
    return getTestMethod("SomeTest_OnlySame.java", "somethingS.java");
  }

  private PsiMethod getTestMethod(String classFileName, String... testDataFiles) {
    myFixture.configureByFiles(classFileName);
    for (String file : testDataFiles) {
      myFixture.copyFileToProject(file, RESOURCES_ROOT_NAME + "/" + file);
    }

    PsiMethod testMethod = PsiTreeUtil.getParentOfType(myFixture.getElementAtCaret(), PsiMethod.class, false);
    assertNotNull(testMethod);
    return testMethod;
  }

  private static void verifyResultForBeforeAndAfter(List<TestDataFile> testDataFiles) {
    List<String> names = ContainerUtil.map(testDataFiles, TestDataFile::getName);
    assertNotNull(names);
    assertEquals(names.toString(), 2, names.size());
    Collections.sort(names);
    assertEquals("somethingBA_after.java", names.get(0));
    assertEquals("somethingBA_before.java", names.get(1));
  }

  private static void verifyResultForBeforeAndSame(List<TestDataFile> testDataFiles) {
    List<String> names = ContainerUtil.map(testDataFiles, TestDataFile::getName);
    assertNotNull(names);
    assertEquals(names.toString(), 2, names.size());
    Collections.sort(names);
    assertEquals("somethingBS.java", names.get(0));
    assertEquals("somethingBS_before.java", names.get(1));
  }

  private static void verifyResultForAfterAndSame(List<TestDataFile> testDataFiles) {
    List<String> names = ContainerUtil.map(testDataFiles, TestDataFile::getName);
    assertNotNull(names);
    assertEquals(names.toString(), 2, names.size());
    Collections.sort(names);
    assertEquals("somethingAS.java", names.get(0));
    assertEquals("somethingAS_after.java", names.get(1));
  }

  private static void verifyResultForOnlySame(List<TestDataFile> testDataFiles) {
    List<String> names = ContainerUtil.map(testDataFiles, TestDataFile::getName);
    assertNotNull(names);
    assertSize(1, names);
    assertEquals("somethingS.java", names.get(0));
  }
}
