// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import org.jetbrains.annotations.NotNull;

/**
 * @author Roman.Chernyatchik
 */
public interface BundledQuickListsProvider {
  /**
   * Provides custom bundled actions quick lists.
   * @return Array of relative paths without extensions for lists.
   * E.g. : ["/quickLists/myList", "otherList"] for quickLists/myList.xml, otherList.xml
   */
  String @NotNull [] getBundledListsRelativePaths();
}
