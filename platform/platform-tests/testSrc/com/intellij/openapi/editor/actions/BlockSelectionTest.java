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
package com.intellij.openapi.editor.actions;

import com.intellij.ide.ClipboardSynchronizer;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.awt.datatransfer.StringSelection;

public class BlockSelectionTest extends BasePlatformTestCase {

  public void test_paste_tabs_into_block_selection() {
    myFixture.configureByText("a.txt", """
      <li></li>
      <li></li>
      """);
    myFixture.getEditor().getSelectionModel().setBlockSelection(new LogicalPosition(0, 4), new LogicalPosition(1, 4));
    StringSelection content = new StringSelection("""
                                                    \tCo-ed basketball
                                                    \tCo-ed handball""");
    ClipboardSynchronizer.getInstance().setContent(content, content);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE);
    myFixture.checkResult("""
                            <li>\tCo-ed basketball</li>
                            <li>\tCo-ed handball</li>
                            """);
  }

  public void test_paste_single_tabbed_string_into_block_selection() {
    myFixture.configureByText("a.txt", """
      <li></li>
      <li></li>
      """);
    myFixture.getEditor().getSelectionModel().setBlockSelection(new LogicalPosition(0, 4), new LogicalPosition(1, 4));
    StringSelection content = new StringSelection("\tCo-ed handball");
    ClipboardSynchronizer.getInstance().setContent(content, content);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE);
    myFixture.checkResult("""
                            <li>\tCo-ed handball</li>
                            <li>\tCo-ed handball</li>
                            """);
  }
}
