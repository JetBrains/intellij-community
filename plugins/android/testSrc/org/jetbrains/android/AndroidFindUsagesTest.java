/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.android;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.PsiElementUsageTarget;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidFindUsagesTest extends AndroidTestCase {
  private static final String BASE_PATH = "/findUsages/";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject(BASE_PATH + "picture3.gif", "res/drawable/picture3.gif");
    myFixture.copyFileToProject("R.java", "gen/p1/p2/R.java");
  }

  public List<UsageInfo> findCodeUsages(String path, String pathInProject) throws Throwable {
    Collection<UsageInfo> usages = findUsages(path, myFixture, pathInProject);
    List<UsageInfo> result = new ArrayList<UsageInfo>();
    for (UsageInfo usage : usages) {
      if (!usage.isNonCodeUsage) {
        result.add(usage);
      }
    }
    return result;
  }

  public void testFileResource() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "styles.xml", "res/values/styles.xml");
    Collection<UsageInfo> references = findCodeUsages("fu1_layout.xml", "res/layout/fu1_layout.xml");
    assertEquals(3, references.size());
  }

  public void testValueResource() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findCodeUsages("fu2_layout.xml", "res/layout/fu2_layout.xml");
    assertEquals(2, references.size());
  }

  public void testValueResource1() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu1_values.xml", "res/values/fu1_values.xml");
    assertEquals(2, references.size());
  }

  public void testValueResource2() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu2_values.xml", "res/values/fu2_values.xml");
    assertEquals(2, references.size());
  }

  public void testValueResource3() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu3_values.xml", "res/values/fu3_values.xml");
    assertEquals(2, references.size());
  }

  public void testValueResource4() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu4_values.xml", "res/values/fu4_values.xml");
    assertEquals(2, references.size());
  }

  public void testValueResource5() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu5_values.xml", "res/values/fu5_values.xml");
    assertEquals(2, references.size());
  }

  public void testValueResource6() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu6_values.xml", "res/values/fu6_values.xml");
    assertEquals(2, references.size());
  }

  public void testValueResource7() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu7_values.xml", "res/values/fu7_values.xml");
    assertEquals(2, references.size());
  }

  public void testValueResource8() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu8_values.xml", "res/values/f8_values.xml");
    assertEquals(2, references.size());
  }

  public void testStyleInheritance() throws Throwable {
    Collection<UsageInfo> references = findCodeUsages("fu10_values.xml", "res/values/f10_values.xml");
    assertEquals(3, references.size());
  }

  public void testStyleInheritance1() throws Throwable {
    Collection<UsageInfo> references = findCodeUsages("fu11_values.xml", "res/values/f11_values.xml");
    assertEquals(3, references.size());
  }

  public void testValueItemResource() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findCodeUsages("fu5_layout.xml", "res/layout/fu5_layout.xml");
    assertEquals(2, references.size());
  }

  public void testFileResourceField() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("Fu3.java", "src/p1/p2/Fu3.java");
    assertEquals(2, references.size());
  }

  public void testValueResourceField() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("Fu4.java", "src/p1/p2/Fu4.java");
    assertEquals(2, references.size());
  }

  public void testValueItemResourceField() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("Fu6.java", "src/p1/p2/Fu6.java");
    assertEquals(2, references.size());
  }

  public void testIdResource() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findCodeUsages("fu7_layout.xml", "res/layout/fu7_layout.xml");
    assertEquals(2, references.size());
  }

  public void testIdResourceField() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("Fu8.java", "src/p1/p2/Fu8.java");
    assertEquals(2, references.size());
  }

  public void testIdResourceDeclaration() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findCodeUsages("fu9_layout.xml", "res/layout/fu9_layout.xml");
    assertEquals(2, references.size());
  }

  public void testStringArray() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findCodeUsages("stringArray.xml", "res/layout/stringArray.xml");
    assertEquals(2, references.size());
  }

  private static Collection<UsageInfo> findUsages(String fileName, final JavaCodeInsightTestFixture fixture, String newFilePath)
    throws Throwable {
    VirtualFile file = fixture.copyFileToProject(BASE_PATH + fileName, newFilePath);
    fixture.configureFromExistingVirtualFile(file);

    final UsageTarget[] targets = UsageTargetUtil.findUsageTargets(new DataProvider() {
      @Override
      public Object getData(@NonNls String dataId) {
        return ((EditorEx)fixture.getEditor()).getDataContext().getData(dataId);
      }
    });

    assert targets != null && targets.length > 0 && targets[0] instanceof PsiElementUsageTarget;
    return fixture.findUsages(((PsiElementUsageTarget)targets[0]).getElement());
  }

  public static Collection<UsageInfo> findUsages(VirtualFile file, JavaCodeInsightTestFixture fixture) throws Exception {
    fixture.configureFromExistingVirtualFile(file);
    final PsiElement targetElement = TargetElementUtilBase
      .findTargetElement(fixture.getEditor(),
                         TargetElementUtilBase.ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    assert targetElement != null;
    return fixture.findUsages(targetElement);
  }

}
