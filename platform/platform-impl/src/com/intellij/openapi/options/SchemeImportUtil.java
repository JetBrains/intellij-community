// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
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
import java.util.Locale;

public final class SchemeImportUtil {
  public static @Nullable VirtualFile selectImportSource(
    @NotNull String @NotNull [] sourceExtensions,
    @NotNull Component parent,
    @Nullable VirtualFile preselect,
    @Nullable @NlsContexts.Label String description
  ) {
    var descriptor = new FileChooserDescriptor(true, false, canSelectJarFile(sourceExtensions), false, false, false);
    if (sourceExtensions.length == 1) {
      descriptor.withExtensionFilter(sourceExtensions[0]);
    }
    else if (sourceExtensions.length > 1) {
      descriptor.withExtensionFilter(IdeCoreBundle.message("file.chooser.files.label", sourceExtensions[0].toUpperCase(Locale.ROOT)), sourceExtensions);
    }
    if (description != null) {
      descriptor.setDescription(description);
    }
    var fileChooser = FileChooserFactory.getInstance().createFileChooser(descriptor, null, parent);
    var preselectFiles = preselect != null ? new VirtualFile[]{preselect} : VirtualFile.EMPTY_ARRAY;
    var virtualFiles = fileChooser.choose(null, preselectFiles);
    if (virtualFiles.length != 1) return null;
    virtualFiles[0].refresh(false, false);
    return virtualFiles[0];
  }

  private static boolean canSelectJarFile(String[] sourceExtensions) {
    return ArrayUtil.contains("jar", sourceExtensions);
  }

  public static @NotNull Element loadSchemeDom(@NotNull VirtualFile file) throws SchemeImportException {
    try (var inputStream = file.getInputStream()) {
      return JDOMUtil.load(inputStream);
    }
    catch (IOException | JDOMException e) {
      throw new SchemeImportException();
    }
  }
}
