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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.usageView.UsageInfo;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 5, 2009
 * Time: 4:48:01 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidFindUsagesTest extends AndroidTestCase {
  private static final String BASE_PATH = "/findUsages/";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "picture3.gif", "res/drawable/picture3.gif");
    myFixture.copyFileToProject(BASE_PATH + "R.java", "gen/p1/p2/R.java");
  }

  public List<UsageInfo> findCodeUsages(String path) throws Throwable {
    Collection<UsageInfo> usages = findElementAtCaret(path, myFixture, BASE_PATH);
    List<UsageInfo> result = new ArrayList<UsageInfo>();
    for (UsageInfo usage : usages) {
      if (!usage.isNonCodeUsage) {
        result.add(usage);
      }
    }
    return result;
  }

  public void testFileResource() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "styles.xml", "res/values/styles.xml");
    Collection<UsageInfo> references = findCodeUsages("fu1_layout.xml");
    assertEquals(3, references.size());
  }

  public void testValueResource() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findCodeUsages("fu2_layout.xml");
    assertEquals(2, references.size());
  }

  public void testValueItemResource() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findCodeUsages("fu5_layout.xml");
    assertEquals(2, references.size());
  }

  public void testFileResourceField() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("Fu3.java");
    assertEquals(2, references.size());
  }

  public void testValueResourceField() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("Fu4.java");
    assertEquals(2, references.size());
  }

  public void testValueItemResourceField() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("Fu6.java");
    assertEquals(2, references.size());
  }

  public void testIdResource() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findCodeUsages("fu7_layout.xml");
    assertEquals(2, references.size());
  }

  public void testIdResourceField() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("Fu8.java");
    assertEquals(2, references.size());
  }

  public void testIdResourceDeclaration() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findCodeUsages("fu9_layout.xml");
    assertEquals(2, references.size());
  }

  public void testStringArray() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findCodeUsages("stringArray.xml");
    assertEquals(2, references.size());
  }

  private static Collection<UsageInfo> findElementAtCaret(String fileName, JavaCodeInsightTestFixture fixture, String basePath) throws Throwable {
    String newFilePath = "res/layout/" + fileName;
    VirtualFile file = fixture.copyFileToProject(basePath + fileName, newFilePath);
    return findUsages(file, fixture);
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
