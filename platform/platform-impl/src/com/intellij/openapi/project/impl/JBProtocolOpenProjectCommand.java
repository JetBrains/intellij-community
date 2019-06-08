// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.JBProtocolCommand;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
final class JBProtocolOpenProjectCommand extends JBProtocolCommand {
  JBProtocolOpenProjectCommand() {
    super("open");
  }

  @Override
  public void perform(String target, @NotNull Map<String, String> parameters) {
    String projectPath = StringUtil.trimStart(target, LocalFileSystem.PROTOCOL_PREFIX);
    ApplicationManager.getApplication().invokeLater(
      () -> ProjectUtil.openProject(projectPath, null, true), ModalityState.NON_MODAL);
  }
}
