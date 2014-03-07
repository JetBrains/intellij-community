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
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.FileBasedTestCaseHelper;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;

@SuppressWarnings("JUnit4AnnotatedMethodInJUnit3TestCase")
@RunWith(com.intellij.testFramework.Parameterized.class)
@TestDataPath("/testData/../../../platform/platform-tests/testData/editor/multiCaret/")
public class EditorMultiCaretActionTest extends LightPlatformCodeInsightTestCase implements FileBasedTestCaseHelper {
  @Test
  public void testAction() {
    new WriteCommandAction<Void>(null) {
      @Override
      protected void run(@NotNull Result<Void> result) throws Throwable {
        configureByFile(getBeforeFileName());
        EditorTestUtil.setEditorVisibleSize(myEditor, 120, 20); // some actions require visible area to be defined, like EditorPageUp
        executeAction(getActionName());
        checkResultByFile(getAfterFileName());
      }
    }.execute();
  }

  @Nullable
  @Override
  public String getFileSuffix(String fileName) {
    int pos = fileName.indexOf("-before.");
    if (pos < 0) {
      return null;
    }
    return pos < 0 ? null : fileName.substring(0, pos) + '(' + fileName.substring(pos + 8) + ')';
  }

  private String getBeforeFileName() {
    int pos = myFileSuffix.indexOf('(');
    return myFileSuffix.substring(0, pos) + "-before." + myFileSuffix.substring(pos + 1, myFileSuffix.length() - 1);
  }

  private String getAfterFileName() {
    int pos = myFileSuffix.indexOf('(');
    return myFileSuffix.substring(0, pos) + "-after." + myFileSuffix.substring(pos + 1, myFileSuffix.length() - 1);
  }

  private String getActionName() {
    return myFileSuffix.split("[-(]")[0];
  }
}
