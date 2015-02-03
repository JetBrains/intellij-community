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
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;

/**
 * User: ksafonov
 */
public class XCompareWithClipboardAction extends XFetchValueActionBase {

  @Override
  protected void handle(final Project project, final String value, XDebuggerTree tree) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        DiffRequest request = DiffRequestFactory.getInstance().createClipboardVsValue(value);
        DiffManager.getInstance().showDiff(project, request);
      }
    });
  }
}
