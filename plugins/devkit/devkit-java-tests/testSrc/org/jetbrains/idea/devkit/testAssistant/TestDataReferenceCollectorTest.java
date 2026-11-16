// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@TestDataPath("$PROJECT_ROOT/community/plugins/devkit/devkit-java-tests/testData/")
public class TestDataReferenceCollectorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, DevkitJavaTestsUtil.TESTDATA_PATH);

  private String testName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    testName = testInfo.getTestMethod().orElseThrow().getName().substring("test".length());
  }

  @Test
  public void testDoTestParameters() {
    final List<String> references = doTest();
    assertEquals(1, references.size());
    assertEquals("doTestParameters.java", references.get(0));
  }

  @Test
  public void testDoFileTest() {
    final List<String> references = doTest();
    assertEquals(2, references.size());
    assertTrue(references.contains("before"));
    assertTrue(references.contains("after"));
  }

  @Test
  public void testReferencesInAnyMethod() {
    final List<String> references = doTest();
    assertEquals(1, references.size());
    assertEquals("before", references.get(0));
  }

  @Test
  public void testTestNameAsParameter() {
    final List<String> references = doTest();
    assertEquals(1, references.size());
    assertEquals("beforeTestNameAsParameter", references.get(0));
  }

  @Test
  public void testTestMetadataData() {
    final List<String> references = doTest();
    String contentRoot = contentRootPath();
    assertEquals(3, references.size());
    assertEquals(contentRoot + "/testData/refactoring/introduceVariable/SomeType.kt", references.get(0));
    assertEquals(contentRoot + "/testData/refactoring/introduceVariable/AnonymousType.kt", references.get(1));
    assertEquals(contentRoot + "/testData/refactoring/introduceVariable/extra/AnonymousType.kt", references.get(2));
  }

  @Test
  public void testTestMetadataDataNoTopLevel() {
    final List<String> references = doTest();
    String contentRoot = contentRootPath();
    assertEquals(2, references.size());
    assertEquals(contentRoot + "/testData/refactoring/introduceVariable/AnonymousType.kt", references.get(0));
    assertEquals(contentRoot + "/testData/refactoring/introduceVariable/extra/AnonymousType.kt", references.get(1));
  }

  @Test
  public void testAbstractMethod() {
    final List<String> references = doTest();
    assertEquals(1, references.size());
    assertEquals("abstractMethod.java", references.get(0));
  }

  /**
   * Returns the path of the module content root. `$CONTENT_ROOT` in `@TestDataPath` of the test data resolves to it.
   */
  private String contentRootPath() {
    return codeInsightFixture.get().getTempDirFixture().getFile("").getPath();
  }

  private List<String> doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("referenceCollector/" + testName + ".java");
    return ReadAction.computeBlocking(() -> {
      for (PsiClass aClass : ((PsiJavaFile)myFixture.getFile()).getClasses()) {
        if (aClass.getName().equals("ATest")) {
          Set<PsiClass> classes = new LinkedHashSet<>();
          classes.add(aClass);
          for (PsiClass innerClass : aClass.getAllInnerClasses()) {
            classes.add(innerClass);
            classes.addAll(Arrays.asList(innerClass.getAllInnerClasses()));
          }
          List<TestDataFile> testDataFiles = new ArrayList<>();
          for (PsiClass psiClass : classes) {
            String testDataPath = TestDataLineMarkerProvider.getTestDataBasePath(psiClass);
            for(final PsiMethod theMethod : psiClass.getMethods()) {
              if (theMethod.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
                testDataFiles.addAll(
                  new TestDataReferenceCollector(testDataPath != null ? testDataPath : "", theMethod.getName().substring(4)).collectTestDataReferences(theMethod));
              }
            }
          }
          return ContainerUtil.map(testDataFiles, f -> f.getPath());
        }
      }
      throw new RuntimeException("Couldn't find class ATest in test data file");
    });
  }
}
