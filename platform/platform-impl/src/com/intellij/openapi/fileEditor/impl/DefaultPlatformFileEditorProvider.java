// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.fileEditor.FileEditorProvider;

/**
 * Shows whether the editor, which is created by this provider, is default IDEA's editor
 *
 * @see com.intellij.openapi.fileEditor.FileEditorPolicy#HIDE_DEFAULT_EDITOR
 */
public interface DefaultPlatformFileEditorProvider extends FileEditorProvider {
}
