// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

/**
 * Marker interface for disabling the Preview Tab functionality in some cases
 * At the moment, it works on VirtualFile objects only, but can be applied later on other types.
 * If a virtual file marked with the interface, the corresponding editor will be opened in a regular tab.
 *
 * @author Konstantin Bulenkov
 */
public interface NotSuitableForPreviewTab {
}
