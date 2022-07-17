// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.FileSystem;
import java.nio.file.InvalidPathException;

/**
 * Ensures that an input string is non-blank and represents a valid relative path in the given file system.
 * Multi-component paths and traversals are allowed.
 */
class FileNameInputValidator implements InputValidatorEx {
  private final FileSystem myFileSystem;

  FileNameInputValidator(@NotNull FileSystem fileSystem) {
    myFileSystem = fileSystem;
  }

  @Override
  public @Nullable @NlsContexts.HintText String getErrorText(String input) {
    input = input.trim();

    if (input.isEmpty()) {
      return UIBundle.message("file.name.validator.empty");
    }

    try {
      if (myFileSystem.getPath(input).isAbsolute()) {
        return UIBundle.message("file.name.validator.absolute");
      }
    }
    catch (InvalidPathException e) {
      return UIBundle.message("file.name.validator.invalid", e.getMessage());
    }

    return null;
  }
}
