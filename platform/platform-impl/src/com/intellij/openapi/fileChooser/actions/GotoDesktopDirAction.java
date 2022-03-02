// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserPanel;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.SystemProperties;
import com.sun.jna.platform.win32.*;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.openapi.util.NullableLazyValue.lazyNullable;

public final class GotoDesktopDirAction extends FileChooserAction implements LightEditCompatible {
  private final NullableLazyValue<Path> myDesktopPath = lazyNullable(() -> {
    Path path = getDesktopDirectory();
    return Files.isDirectory(path) ? path : null;
  });

  private final NullableLazyValue<VirtualFile> myDesktopDirectory = lazyNullable(() -> {
    Path path = myDesktopPath.getValue();
    return path != null ? LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) : null;
  });

  @Override
  protected void update(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    Path path = myDesktopPath.getValue();
    e.getPresentation().setEnabled(path != null);
  }

  @Override
  protected void actionPerformed(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    Path path = myDesktopPath.getValue();
    if (path != null) {
      panel.load(path);
    }
  }

  @Override
  protected void update(@NotNull FileSystemTree tree, @NotNull AnActionEvent e) {
    VirtualFile dir = myDesktopDirectory.getValue();
    e.getPresentation().setEnabled(dir != null && tree.isUnderRoots(dir));
  }

  @Override
  protected void actionPerformed(@NotNull FileSystemTree tree, @NotNull AnActionEvent e) {
    VirtualFile dir = myDesktopDirectory.getValue();
    if (dir != null) {
      tree.select(dir, () -> tree.expand(dir, null));
    }
  }

  private static Path getDesktopDirectory() {
    if (SystemInfo.isWindows && JnaLoader.isLoaded()) {
      char[] path = new char[WinDef.MAX_PATH];
      WinNT.HRESULT res = Shell32.INSTANCE.SHGetFolderPath(null, ShlObj.CSIDL_DESKTOP, null, ShlObj.SHGFP_TYPE_CURRENT, path);
      if (WinError.S_OK.equals(res)) {
        int len = 0;
        while (len < path.length && path[len] != 0) len++;
        return Path.of(new String(path, 0, len));
      }
    }
    else if (SystemInfo.isMac && JnaLoader.isLoaded()) {
      ID manager = Foundation.invoke(Foundation.getObjcClass("NSFileManager"), "defaultManager");
      ID url = Foundation.invoke(manager, "URLForDirectory:inDomain:appropriateForURL:create:error:",
        12 /*NSDesktopDirectory*/, 1 /*NSUserDomainMask*/, null, false, null);
      String path = Foundation.toStringViaUTF8(Foundation.invoke(url, "path"));
      if (path != null) {
        return Path.of(path);
      }
    }
    else if (SystemInfo.hasXdgOpen()) {
      String path = ExecUtil.execAndReadLine(new GeneralCommandLine("xdg-user-dir", "DESKTOP"));
      if (path != null && !path.isBlank()) {
        return Path.of(path);
      }
    }

    return Path.of(SystemProperties.getUserHome(), "Desktop");
  }
}
