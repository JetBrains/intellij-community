// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

import java.util.*;

@TestDataPath("$CONTENT_ROOT/testData/")
public class TestDataReferenceCollectorTest extends LightJavaCodeInsightFixtureTestCase {
  public void testDoTestParameters() {
    final List<String> references = doTest();
    assertEquals(1, references.size());
    assertEquals("doTestParameters.java", references.get(0));
  }

  public void testDoFileTest() {
    final List<String> references = doTest();
    assertEquals(2, references.size());
    assertTrue(references.contains("before"));
    assertTrue(references.contains("after"));
  }

  public void testReferencesInAnyMethod() {
    final List<String> references = doTest();
    assertEquals(1, references.size());
    assertEquals("before", references.get(0));
  }

  public void testTestNameAsParameter() {
    final List<String> references = doTest();
    assertEquals(1, references.size());
    assertEquals("beforeTestNameAsParameter", references.get(0));
  }

  public void testTestMetadataData() {
    final List<String> references = doTest();
    assertEquals(3, references.size());
    assertEquals("/src/testData/refactoring/introduceVariable/SomeType.kt", references.get(0));
    assertEquals("/src/testData/refactoring/introduceVariable/AnonymousType.kt", references.get(1));
    assertEquals("/src/testData/refactoring/introduceVariable/extra/AnonymousType.kt", references.get(2));
  }

  public void testTestMetadataDataNoTopLevel() {
    final List<String> references = doTest();
    assertEquals(2, references.size());
    assertEquals("/src/testData/refactoring/introduceVariable/AnonymousType.kt", references.get(0));
    assertEquals("/src/testData/refactoring/introduceVariable/extra/AnonymousType.kt", references.get(1));
  }

  public void testAbstractMethod() {
    final List<String> references = doTest();
    assertEquals(1, references.size());
    assertEquals("abstractMethod.java", references.get(0));
  }

  private List<String> doTest() {
    myFixture.configureByFile("referenceCollector/" + getTestName(false) + ".java");
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
  }

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH;
  }
}
