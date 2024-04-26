// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

public final class SchemeImportUtil {
  public static @Nullable VirtualFile selectImportSource(final String @NotNull [] sourceExtensions,
                                                         @NotNull Component parent,
                                                         @Nullable VirtualFile preselect,
                                                         @Nullable @NlsContexts.Label String description) {
    final Set<String> extensions = Set.of(sourceExtensions);
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, canSelectJarFile(sourceExtensions), false, false, false) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        return
          (file.isDirectory() || isFileSelectable(file)) &&
          (showHiddenFiles || !FileElement.isFileHidden(file));
      }

      @Override
      public boolean isFileSelectable(@Nullable VirtualFile file) {
        return file != null && !file.isDirectory() && file.getExtension() != null && extensions.contains(file.getExtension());
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

  private static boolean canSelectJarFile(String @NotNull [] sourceExtensions) {
    return ArrayUtil.contains("jar", sourceExtensions);
  }

  public static @NotNull Element loadSchemeDom(@NotNull VirtualFile file) throws SchemeImportException {
    try (InputStream inputStream = file.getInputStream()) {
      return JDOMUtil.load(inputStream);
    }
    catch (IOException | JDOMException e) {
      throw new SchemeImportException();
    }
  }

}
