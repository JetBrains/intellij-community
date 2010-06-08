/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.testFramework.LightProjectDescriptor;

/**
 * @author Roman Chernyatchik
 */
public class FileUrlLocationTest extends SMLightFixtureTestCase {
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourDescriptor;
  }

  public void testSpecNavigation() throws Throwable {
    createAndAddFile("my_example_spec.xml",
                     "\n" +
                     "<describe>\n" +
                     "    <a></a>\n" +
                     "</describe>\n" +
                     "\n");

    final String path = myFixture.getFile().getVirtualFile().getPath();
    doTest(1, "<", path, 2);
    doTest(16, "<", path, 3);
  }

  private void doTest(final int expectedOffset, final String expectedStartsWith,
                      final String filePath, final int lineNum) {
    final SMTestProxy testProxy =
        new SMTestProxy("myTest", false, "file://" + filePath + ":" + lineNum);

    final Location location = testProxy.getLocation(getProject());
    assertNotNull(location);
    assertNotNull(location.getPsiElement());

    //System.out.println(location.getPsiElement().getText());
    //System.out.println(location.getPsiElement().getTextOffset());
    assertEquals(expectedOffset, location.getPsiElement().getTextOffset());
    final String element = location.getPsiElement().getText();
    assertTrue(element, element.startsWith(expectedStartsWith));
  }
}
