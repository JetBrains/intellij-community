/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 31, 2002
 * Time: 3:02:52 PM
 * To change template for new interface use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.diff.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diff.DiffPanel;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.processing.HighlightMode;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Deprecated
public interface DiffPanelEx extends DiffPanel, Disposable {
  @Nullable
  Editor getEditor1();
  @Nullable
  Editor getEditor2();

  DiffPanelOptions getOptions();

  void setComparisonPolicy(@NotNull ComparisonPolicy comparisonPolicy);

  ComparisonPolicy getComparisonPolicy();

  void setAutoScrollEnabled(boolean enabled);

  boolean isAutoScrollEnabled();

  void setHighlightMode(@NotNull HighlightMode highlightMode);

  @NotNull
  HighlightMode getHighlightMode();
}
