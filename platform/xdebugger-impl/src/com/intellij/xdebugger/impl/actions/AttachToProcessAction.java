// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.attach.XAttachDebuggerProvider;

public class AttachToProcessAction extends AttachToProcessActionBase {
  public AttachToProcessAction() {
    super(XDebuggerBundle.message("xdebugger.attach.action"),
          XDebuggerBundle.message("xdebugger.attach.action.description"),
          AllIcons.Debugger.AttachToProcess,
          XAttachDebuggerProvider.EP::getExtensionList,
          XDebuggerBundle.message("xdebugger.attach.popup.selectDebugger.title")
    );
  }
}
