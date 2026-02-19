// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.ex;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.fileTypes.FileType;

public final class FileChooserKeys {
  public static final DataKey<FileType> NEW_FILE_TYPE = DataKey.create("NewFileType");
  public static final DataKey<String> NEW_FILE_TEMPLATE_TEXT = DataKey.create("NewFileTemplateText");
  public static final DataKey<Boolean> DELETE_ACTION_AVAILABLE = DataKey.create("FileChooserDeleteActionAvailable");
}
