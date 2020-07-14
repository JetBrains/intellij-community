// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Allows compact middle directories feature for directory-oriented usage groups
 * It is used in construction of a usage tree within a GroupNode
 * It indicates that a correspondent GroupNode can be merged with/being splitted by/or split another node with a CompactGroup
 */
public interface CompactGroup {
  boolean hasCommonParent(@NotNull CompactGroup group);

  boolean isParentOf(@NotNull CompactGroup group);

  /**
   * /**
   * Merges this group with a group (if the group is a child group)
   * otherwise does nothing and returns false
   *
   * @return new merged group
   */
  CompactGroup merge(@NotNull CompactGroup group);

  /**
   * Creates a list of 3 groups: parent group and two relative groups if this group and a @param group have a common parent,
   * if one of them is a parent of the other creates two groups parent group and a relative group
   * If @param group is a subGroup of this group does nothing if doNothingIfSubGroup is true
   * if @param doNothingIfSubGroup is true then the child group will not be splitted by the parent
   */

  @NotNull List<CompactGroup> split(@NotNull CompactGroup group, boolean doNothingIfSubGroup);
}