// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.fileChooser.ex.FileSaverDialogImpl;
import com.intellij.openapi.fileChooser.ex.FileTextFieldImpl;
import com.intellij.openapi.fileChooser.ex.LocalFsFinder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.mac.MacFileSaverDialog;
import com.intellij.ui.mac.MacPathChooserDialog;
import com.intellij.ui.win.WinPathChooserDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class LocalFileChooserFactory implements ClientFileChooserFactory {
  @Override
  public @NotNull FileChooserDialog createFileChooser(@NotNull FileChooserDescriptor descriptor,
                                                      @Nullable Project project,
                                                      @Nullable Component parent) {
    if (ClientFileChooserFactory.useNativeMacChooser(descriptor)) {
      return new MacPathChooserDialog(descriptor, parent, project);
    }
    else if (ClientFileChooserFactory.useNativeWinChooser(descriptor)) {
      return new WinPathChooserDialog(descriptor, parent, project);
    }
    else if (ClientFileChooserFactory.useNewChooser(descriptor)) {
      return new NewFileChooserDialogImpl(descriptor, parent, project);
    }
    else if (parent != null) {
      return new FileChooserDialogImpl(descriptor, parent, project);
    }
    else {
      return new FileChooserDialogImpl(descriptor, project);
    }
  }

  @Override
  public @NotNull PathChooserDialog createPathChooser(@NotNull FileChooserDescriptor descriptor,
                                                      @Nullable Project project,
                                                      @Nullable Component parent) {
    PathChooserDialog chooser = ClientFileChooserFactory.createNativePathChooserIfEnabled(descriptor, project, parent);
    if (chooser != null) {
      return chooser;
    }
    else if (ClientFileChooserFactory.useNewChooser(descriptor)) {
      return new NewFileChooserDialogImpl(descriptor, parent, project);
    }
    else if (parent != null) {
      return new FileChooserDialogImpl(descriptor, parent, project);
    }
    else {
      return new FileChooserDialogImpl(descriptor, project);
    }
  }

  @Override
  public @NotNull FileTextField createFileTextField(@NotNull FileChooserDescriptor descriptor, boolean showHidden, @Nullable Disposable parent) {
    return new FileTextFieldImpl(new JTextField(), new LocalFsFinder(), new LocalFsFinder.FileChooserFilter(descriptor, showHidden),
                                 ClientFileChooserFactory.getMacroMap(), parent);
  }

  @Override
  public void installFileCompletion(@NotNull JTextField field,
                                    @NotNull FileChooserDescriptor descriptor,
                                    boolean showHidden,
                                    @Nullable Disposable parent) {
    if (!ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      new FileTextFieldImpl(field, new LocalFsFinder(), new LocalFsFinder.FileChooserFilter(descriptor, showHidden),
                            ClientFileChooserFactory.getMacroMap(), parent);
    }
  }

  @Override
  public @NotNull FileSaverDialog createSaveFileDialog(@NotNull FileSaverDescriptor descriptor, @Nullable Project project) {
    return SystemInfo.isMac && Registry.is("ide.mac.native.save.dialog", true)
           ? new MacFileSaverDialog(descriptor, project) : new FileSaverDialogImpl(descriptor, project);
  }

  @Override
  public @NotNull FileSaverDialog createSaveFileDialog(@NotNull FileSaverDescriptor descriptor, @NotNull Component parent) {
    return SystemInfo.isMac && Registry.is("ide.mac.native.save.dialog", true)
           ? new MacFileSaverDialog(descriptor, parent) : new FileSaverDialogImpl(descriptor, parent);
  }
}
