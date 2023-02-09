// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.fileEditor.FileEditor;
import org.jetbrains.annotations.Nullable;

public interface CurrentEditorProvider {
  @Nullable FileEditor getCurrentEditor();
}
