// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class FileChooserFactoryImpl extends FileChooserFactory {
  @Override
  public @NotNull FileChooserDialog createFileChooser(@NotNull FileChooserDescriptor descriptor,
                                                      @Nullable Project project,
                                                      @Nullable Component parent) {
    return ClientFileChooserFactory.getInstance().createFileChooser(descriptor, project, parent);
  }

  @Override
  public @NotNull PathChooserDialog createPathChooser(@NotNull FileChooserDescriptor descriptor,
                                                      @Nullable Project project,
                                                      @Nullable Component parent) {
    return ClientFileChooserFactory.getInstance().createPathChooser(descriptor, project, parent);
  }

  @Override
  public @NotNull FileTextField createFileTextField(@NotNull FileChooserDescriptor descriptor, boolean showHidden, @Nullable Disposable parent) {
    return ClientFileChooserFactory.getInstance().createFileTextField(descriptor, showHidden, parent);
  }

  @Override
  public void installFileCompletion(@NotNull JTextField field,
                                    @NotNull FileChooserDescriptor descriptor,
                                    boolean showHidden,
                                    @Nullable Disposable parent) {
    ClientFileChooserFactory.getInstance().installFileCompletion(field, descriptor, showHidden, parent);
  }

  @Override
  public @NotNull FileSaverDialog createSaveFileDialog(@NotNull FileSaverDescriptor descriptor, @Nullable Project project) {
    return ClientFileChooserFactory.getInstance().createSaveFileDialog(descriptor, project);
  }

  @Override
  public @NotNull FileSaverDialog createSaveFileDialog(@NotNull FileSaverDescriptor descriptor, @NotNull Component parent) {
    return ClientFileChooserFactory.getInstance().createSaveFileDialog(descriptor, parent);
  }

  public static PathChooserDialog createNativePathChooserIfEnabled(@NotNull FileChooserDescriptor descriptor,
                                                                   @Nullable Project project,
                                                                   @Nullable Component parent) {
    return ClientFileChooserFactory.createNativePathChooserIfEnabled(descriptor, project, parent);
  }

  public static Map<String, String> getMacroMap() {
    return ClientFileChooserFactory.getMacroMap();
  }
}
