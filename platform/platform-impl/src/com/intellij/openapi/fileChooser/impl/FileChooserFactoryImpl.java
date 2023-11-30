// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class FileChooserFactoryImpl extends FileChooserFactory {
  private static ClientFileChooserFactory getService() {
    return ApplicationManager.getApplication().getService(ClientFileChooserFactory.class);
  }

  @Override
  public @NotNull FileChooserDialog createFileChooser(@NotNull FileChooserDescriptor descriptor,
                                                      @Nullable Project project,
                                                      @Nullable Component parent) {
    return getService().createFileChooser(descriptor, project, parent);
  }

  @Override
  public @NotNull PathChooserDialog createPathChooser(@NotNull FileChooserDescriptor descriptor,
                                                      @Nullable Project project,
                                                      @Nullable Component parent) {
    return getService().createPathChooser(descriptor, project, parent);
  }

  @Override
  public @NotNull FileTextField createFileTextField(@NotNull FileChooserDescriptor descriptor, boolean showHidden, @Nullable Disposable parent) {
    return getService().createFileTextField(descriptor, showHidden, parent);
  }

  @Override
  public void installFileCompletion(@NotNull JTextField field,
                                    @NotNull FileChooserDescriptor descriptor,
                                    boolean showHidden,
                                    @Nullable Disposable parent) {
    getService().installFileCompletion(field, descriptor, showHidden, parent);
  }

  @Override
  public @NotNull FileSaverDialog createSaveFileDialog(@NotNull FileSaverDescriptor descriptor, @Nullable Project project) {
    return getService().createSaveFileDialog(descriptor, project);
  }

  @Override
  public @NotNull FileSaverDialog createSaveFileDialog(@NotNull FileSaverDescriptor descriptor, @NotNull Component parent) {
    return getService().createSaveFileDialog(descriptor, parent);
  }

  @ApiStatus.Internal
  public static PathChooserDialog createNativePathChooserIfEnabled(@NotNull FileChooserDescriptor descriptor,
                                                                   @Nullable Project project,
                                                                   @Nullable Component parent) {
    return LocalFileChooserFactory.createNativePathChooserIfEnabled(descriptor, project, parent);
  }

  public static Map<String, String> getMacroMap() {
    var macros = PathMacros.getInstance();
    var allNames = macros.getAllMacroNames();
    var map = new HashMap<String, String>(allNames.size());
    for (var eachMacroName : allNames) {
      map.put('$' + eachMacroName + '$', macros.getValue(eachMacroName));
    }
    return map;
  }
}
