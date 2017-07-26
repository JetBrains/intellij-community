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
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.io.IOException;
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
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/guessByExistingFiles";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    moduleBuilder.addLibrary("junit3", JavaSdkUtil.getJunit3JarPath());
  }


  public void testGetTestName() {
    String result = TestDataGuessByExistingFilesUtil.getTestName("testTestName");
    assertEquals("TestName", result);
  }

  public void testCollectTestDataByExistingFilesBeforeAndAfter() throws IOException {
    PsiMethod testMethod = getTestMethodWithBeforeAndAfterTestData();
    List<String> result = TestDataGuessByExistingFilesUtil.collectTestDataByExistingFiles(testMethod);
    verifyResultForBeforeAndAfter(result);
  }

  public void testSuggestTestDataFilesBeforeAndAfter() {
    PsiMethod testMethod = getTestMethodWithBeforeAndAfterTestData();
    List<String> result = TestDataGuessByExistingFilesUtil.suggestTestDataFiles(
      TestDataGuessByExistingFilesUtil.getTestName(testMethod.getName()), null, testMethod.getContainingClass());
    verifyResultForBeforeAndAfter(result);
  }

  public void testCollectTestDataByExistingFilesBeforeAndSame() throws IOException {
    PsiMethod testMethod = getTestMethodWithBeforeAndSameTestData();
    List<String> result = TestDataGuessByExistingFilesUtil.collectTestDataByExistingFiles(testMethod);
    verifyResultForBeforeAndSame(result);
  }

  public void testSuggestTestDataFilesBeforeAndSame() {
    PsiMethod testMethod = getTestMethodWithBeforeAndSameTestData();
    List<String> result = TestDataGuessByExistingFilesUtil.suggestTestDataFiles(
      TestDataGuessByExistingFilesUtil.getTestName(testMethod.getName()), null, testMethod.getContainingClass());
    verifyResultForBeforeAndSame(result);
  }

  public void testCollectTestDataByExistingFilesAfterAndSame() throws IOException {
    PsiMethod testMethod = getTestMethodWithAfterAndSameTestData();
    List<String> result = TestDataGuessByExistingFilesUtil.collectTestDataByExistingFiles(testMethod);
    verifyResultForAfterAndSame(result);
  }

  public void testSuggestTestDataFilesAfterAndSame() {
    PsiMethod testMethod = getTestMethodWithAfterAndSameTestData();
    List<String> result = TestDataGuessByExistingFilesUtil.suggestTestDataFiles(
      TestDataGuessByExistingFilesUtil.getTestName(testMethod.getName()), null, testMethod.getContainingClass());
    verifyResultForAfterAndSame(result);
  }

  public void testCollectTestDataByExistingFilesOnlySame() throws IOException {
    PsiMethod testMethod = getTestMethodWithOnlySameTestData();
    List<String> result = TestDataGuessByExistingFilesUtil.collectTestDataByExistingFiles(testMethod);
    verifyResultForOnlySame(result);
  }

  public void testSuggestTestDataFilesOnlySame() {
    PsiMethod testMethod = getTestMethodWithOnlySameTestData();
    List<String> result = TestDataGuessByExistingFilesUtil.suggestTestDataFiles(
      TestDataGuessByExistingFilesUtil.getTestName(testMethod.getName()), null, testMethod.getContainingClass());
    verifyResultForOnlySame(result);
  }


  private PsiMethod getTestMethodWithBeforeAndAfterTestData() {
    return getTestMethod(
      "SomeTest_BeforeAndAfter.java", "somethingBA_before.java", "somethingBA_after.java");
  }

  private PsiMethod getTestMethodWithBeforeAndSameTestData() {
    return getTestMethod(
      "SomeTest_BeforeAndSame.java", "somethingBS_before.java", "somethingBS.java");
  }

  private PsiMethod getTestMethodWithAfterAndSameTestData() {
    return getTestMethod(
      "SomeTest_AfterAndSame.java", "somethingAS_after.java", "somethingAS.java");
  }

  private PsiMethod getTestMethodWithOnlySameTestData() {
    return getTestMethod(
      "SomeTest_OnlySame.java", "somethingS.java");
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


  private static void verifyResultForBeforeAndAfter(List<String> names) {
    assertNotNull(names);
    assertEquals(names.toString(), 2, names.size());
    names.sort(String::compareTo);
    assertTrue(names.get(0), names.get(0).endsWith("somethingBA_after.java"));
    assertTrue(names.get(1), names.get(1).endsWith("somethingBA_before.java"));
  }

  private static void verifyResultForBeforeAndSame(List<String> names) {
    assertNotNull(names);
    assertEquals(names.toString(), 2, names.size());
    names.sort(String::compareTo);
    assertTrue(names.get(0), names.get(0).endsWith("somethingBS.java"));
    assertTrue(names.get(1), names.get(1).endsWith("somethingBS_before.java"));
  }

  private static void verifyResultForAfterAndSame(List<String> names) {
    assertNotNull(names);
    assertEquals(names.toString(), 2, names.size());
    names.sort(String::compareTo);
    assertTrue(names.get(0), names.get(0).endsWith("somethingAS.java"));
    assertTrue(names.get(1), names.get(1).endsWith("somethingAS_after.java"));
  }

  private static void verifyResultForOnlySame(List<String> names) {
    assertNotNull(names);
    assertEquals(names.toString(), 1, names.size());
    assertTrue(names.get(0), names.get(0).endsWith("somethingS.java"));
  }
}
