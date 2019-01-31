/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.testframework.sm;

import com.intellij.execution.Location;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

import java.util.Collections;

/**
 * @author Roman Chernyatchik
 */
public class FileUrlLocationTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testExcluded() {
    myFixture.addFileToProject("secondary/my_example_spec.xml", "");
    ModuleRootModificationUtil.updateExcludedFolders(myModule, ModuleRootManager.getInstance(myModule).getContentRoots()[0], Collections.emptyList(), Collections.singletonList("/src"));
     VirtualFile file = myFixture.configureByText(
      "my_example_spec.xml",
      "\n" +
      "<describe>\n" +
      "    <a id='1'></a>\n" +
      "</describe>\n" +
      "\n").getVirtualFile();

    doTest(1, file.getPath(), 2, -1);
  }

  public void testSpecNavigation() {
    VirtualFile file = myFixture.configureByText(
      "my_example_spec.xml",
      "\n" +
      "<describe>\n" +
      "    <a id='1'></a>\n" +
      "</describe>\n" +
      "\n").getVirtualFile();

    doTest(1, file.getPath(), 2, -1);
    doTest(16, file.getPath(), 3, -1);
    doTest(2, file.getPath(), 2, 5);
    doTest(19, file.getPath(), 3, 8);
    doTest(0, file.getPath(), 100, -1);
    doTest(11, file.getPath(), 2, 100);
  }

  private void doTest(int expectedOffset, String filePath, int lineNum, int columnNumber) {
    SMTestProxy testProxy = new SMTestProxy("myTest", false, "file://" + filePath + ":" + lineNum
                                                             + (columnNumber > 0 ? (":" + columnNumber) : ""));
    testProxy.setLocator(FileUrlProvider.INSTANCE);

    Location location = testProxy.getLocation(getProject(), GlobalSearchScope.allScope(getProject()));
    assertNotNull(location);
    PsiElement element = location.getPsiElement();
    assertNotNull(element);
    assertEquals(expectedOffset, element.getTextOffset());
  }
}