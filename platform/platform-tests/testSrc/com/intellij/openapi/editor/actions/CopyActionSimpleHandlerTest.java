/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightSettings;

public class CopyActionSimpleHandlerTest extends CopyActionTest {
  private int myPrevValue;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myPrevValue = CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE;
    CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE = CodeInsightSettings.NO;
  }

  @Override
  public void tearDown() throws Exception {
    CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE = myPrevValue;
    super.tearDown();
  }
}
