// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;

public interface FileTypeSelectable {
  void selectFileType(@NotNull FileType fileType);
}
