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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.mac.MacFileSaverDialog;
import com.intellij.ui.mac.MacPathChooserDialog;
import com.intellij.ui.win.WinPathChooserDialog;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class LocalFileChooserFactory implements ClientFileChooserFactory {
  @Override
  public @NotNull FileChooserDialog createFileChooser(@NotNull FileChooserDescriptor descriptor,
                                                      @Nullable Project project,
                                                      @Nullable Component parent) {
    var chooser = createNativePathChooserIfEnabled(descriptor, project, parent);
    return chooser != null ? (FileChooserDialog)chooser :
           useNewChooser(descriptor) ? new NewFileChooserDialogImpl(descriptor, parent, project) :
           parent != null ? new FileChooserDialogImpl(descriptor, parent, project) :
           new FileChooserDialogImpl(descriptor, project);
  }

  @Override
  public @NotNull PathChooserDialog createPathChooser(@NotNull FileChooserDescriptor descriptor,
                                                      @Nullable Project project,
                                                      @Nullable Component parent) {
    var chooser = createNativePathChooserIfEnabled(descriptor, project, parent);
    return chooser != null ? chooser :
           useNewChooser(descriptor) ? new NewFileChooserDialogImpl(descriptor, parent, project) :
           parent != null ? new FileChooserDialogImpl(descriptor, parent, project) :
           new FileChooserDialogImpl(descriptor, project);
  }

  @Override
  public @NotNull FileTextField createFileTextField(@NotNull FileChooserDescriptor descriptor, boolean showHidden, @Nullable Disposable parent) {
    return new FileTextFieldImpl(new JTextField(), new LocalFsFinder(), new LocalFsFinder.FileChooserFilter(descriptor, showHidden),
                                 FileChooserFactoryImpl.getMacroMap(), parent);
  }

  @Override
  @SuppressWarnings("ResultOfObjectAllocationIgnored")
  public void installFileCompletion(@NotNull JTextField field,
                                    @NotNull FileChooserDescriptor descriptor,
                                    boolean showHidden,
                                    @Nullable Disposable parent) {
    if (!ApplicationManager.getApplication().isUnitTestMode() && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      new FileTextFieldImpl(field, new LocalFsFinder(), new LocalFsFinder.FileChooserFilter(descriptor, showHidden),
                            FileChooserFactoryImpl.getMacroMap(), parent);
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

  static @Nullable PathChooserDialog createNativePathChooserIfEnabled(@NotNull FileChooserDescriptor descriptor,
                                                                      @Nullable Project project,
                                                                      @Nullable Component parent) {
    return useNativeMacChooser(descriptor) ? new MacPathChooserDialog(descriptor, parent, project) :
           useNativeWinChooser(descriptor) ? new WinPathChooserDialog(descriptor, parent, project) :
           null;
  }

  private static boolean useNativeMacChooser(FileChooserDescriptor descriptor) {
    return SystemInfo.isMac &&
           SystemInfo.isJetBrainsJvm &&
           !descriptor.isForcedToUseIdeaFileChooser() &&
           Registry.is("ide.mac.file.chooser.native", true);
  }

  private static boolean useNativeWinChooser(FileChooserDescriptor descriptor) {
    return SystemInfo.isWindows &&
           !descriptor.isForcedToUseIdeaFileChooser() &&
           Registry.is("ide.win.file.chooser.native", false);
  }

  private static boolean useNewChooser(FileChooserDescriptor descriptor) {
    return Registry.is("ide.ui.new.file.chooser") && ContainerUtil.and(descriptor.getRoots(), VirtualFile::isInLocalFileSystem);
  }
}
