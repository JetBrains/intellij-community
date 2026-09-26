// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.LineExtensionInfo;


/**
 * Introduced within IDEA-136087 Debugger: Inlined values: background settings are ignored
 */
record LineExtensionData(LineExtensionInfo info, LineLayout layout) {
}
