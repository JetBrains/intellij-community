// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions;

import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.attach.XAttachDebuggerProvider;
import com.intellij.xdebugger.attach.XAttachHostProvider;

public class AttachToProcessAction extends AttachToProcessActionBase {
  public AttachToProcessAction() {
    super(XDebuggerBundle.message("xdebugger.attach.action"),
          XDebuggerBundle.message("xdebugger.attach.action.description"),
          AllIcons.Debugger.AttachToProcess,
          () -> XAttachDebuggerProvider.getAttachDebuggerProviders(),
          () -> XAttachHostProvider.EP.getExtensionList(),
          XDebuggerBundle.message("xdebugger.attach.popup.selectDebugger.title")
    );
  }
}
