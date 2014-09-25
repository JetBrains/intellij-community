/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.platform;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.projectImport.ProjectSetProcessor;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.intellij.lang.annotations.Language;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * @author Dmitry Avdeev
 */
public class ProjectSetTest extends LightPlatformTestCase {

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public ProjectSetTest() {
    PlatformTestCase.initPlatformLangPrefix();
  }

  private static String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/platform/platform-tests/testData/projectSet/";
  }

  public void testProjectSetReader() throws IOException {
    ProjectSetReader reader = new ProjectSetReader();

    final ArrayList<String> entries = new ArrayList<String>();
    PlatformTestUtil.registerExtension(ProjectSetProcessor.EXTENSION_POINT_NAME, new ProjectSetProcessor() {
      @Override
      public String getId() {
        return "test";
      }

      @Override
      public Object interactWithUser() {
        return new Object();
      }

      @Override
      public void processEntry(String key, String value, Object o) {
        entries.add(value);
      }
    }, myTestRootDisposable);

    @Language("JSON") String descriptor = FileUtil.loadFile(new File(getTestDataPath() + "descriptor.json"));
    reader.readDescriptor(descriptor);
    assertEquals(1, entries.size());
  }
}
