/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import com.intellij.openapi.options.advanced.AdvancedSettings;

import static com.intellij.openapi.editor.actions.CopyAction.SKIP_SELECTING_LINE_AFTER_COPY_EMPTY_SELECTION_KEY;

public class CopyActionSkipSelectLineTest extends CopyActionTest {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    boolean savedValue = AdvancedSettings.getBoolean(SKIP_SELECTING_LINE_AFTER_COPY_EMPTY_SELECTION_KEY);
    disposeOnTearDown(() -> {
      AdvancedSettings.setBoolean(SKIP_SELECTING_LINE_AFTER_COPY_EMPTY_SELECTION_KEY, savedValue);
    });
    AdvancedSettings.setBoolean(SKIP_SELECTING_LINE_AFTER_COPY_EMPTY_SELECTION_KEY, true);
  }
}
