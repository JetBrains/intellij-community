// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.JBProtocolCommand;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public final class JBProtocolOpenProjectCommand extends JBProtocolCommand {
  JBProtocolOpenProjectCommand() {
    super("open");
  }

  @Override
  public void perform(String target, @NotNull Map<String, String> parameters) {
    Path projectPath = toPath(target);
    ApplicationManager.getApplication().invokeLater(() -> {
      ProjectUtil.openProject(projectPath, new OpenProjectTask());
    }, ModalityState.NON_MODAL);
  }

  public static Path toPath(String path) {
    return Paths.get(StringUtil.trimStart(path, LocalFileSystem.PROTOCOL_PREFIX)).normalize();
  }
}
