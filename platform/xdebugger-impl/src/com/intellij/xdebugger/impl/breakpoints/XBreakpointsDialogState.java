/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.ide.util.treeView.TreeState;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;

import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
*/
@Tag("breakpoints-dialog")
public class XBreakpointsDialogState {
  private Set<String> mySelectedGroupingRules = new HashSet<>();

  @Transient // Not saved for now
  private TreeState myTreeState = null;

  @XCollection(propertyElementName = "selected-grouping-rules", elementName = "grouping-rule", valueAttributeName = "id")
  public Set<String> getSelectedGroupingRules() {
    return mySelectedGroupingRules;
  }

  public void setSelectedGroupingRules(final Set<String> selectedGroupingRules) {
    mySelectedGroupingRules = selectedGroupingRules;
  }

  @Transient // Not saved for now
  public TreeState getTreeState() {
    return myTreeState;
  }

  public void setTreeState(TreeState treeState) {
    myTreeState = treeState;
  }
}
