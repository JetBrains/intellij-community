// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SchemeImportUtil {
  @Nullable
  public static VirtualFile selectImportSource(@NotNull final String[] sourceExtensions,
                                               @NotNull Component parent,
                                               @Nullable VirtualFile preselect,
                                               @Nullable String description) {
    final Set<String> extensions = new HashSet<>(Arrays.asList(sourceExtensions));
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, canSelectJarFile(sourceExtensions), false, false, false) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        return
          (file.isDirectory() || extensions.contains(file.getExtension())) &&
          (showHiddenFiles || !FileElement.isFileHidden(file));
      }

      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return !file.isDirectory() && extensions.contains(file.getExtension());
      }
    };
    if (description != null) {
      descriptor.setDescription(description);
    }
    FileChooserDialog fileChooser = FileChooserFactory.getInstance()
      .createFileChooser(descriptor, null, parent);
    final VirtualFile[] preselectFiles;
    if (preselect != null) {
      preselectFiles = new VirtualFile[1];
      preselectFiles[0] = preselect;
    }
    else {
      preselectFiles = VirtualFile.EMPTY_ARRAY;
    }
    final VirtualFile[] virtualFiles = fileChooser.choose(null, preselectFiles);
    if (virtualFiles.length != 1) return null;
    virtualFiles[0].refresh(false, false);
    return virtualFiles[0];
  }

  private static boolean canSelectJarFile(@NotNull String[] sourceExtensions) {
    for (String ext : sourceExtensions) {
      if ("jar".equals(ext)) return true;
    }
    return false;
  }

  @NotNull
  public static Element loadSchemeDom(@NotNull VirtualFile file) throws SchemeImportException {
    try (InputStream inputStream = file.getInputStream()) {
      return JDOMUtil.load(inputStream);
    }
    catch (IOException | JDOMException e) {
      throw new SchemeImportException();
    }
  }

}
