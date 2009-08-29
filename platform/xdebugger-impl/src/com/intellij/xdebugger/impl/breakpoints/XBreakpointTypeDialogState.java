package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
*/
@Tag("breakpoints-dialog")
public class XBreakpointTypeDialogState {
  private Set<String> mySelectedGroupingRules = new HashSet<String>();

  @Tag("selected-grouping-rules")
  @AbstractCollection(surroundWithTag = false, elementTag = "grouping-rule", elementValueAttribute = "id")
  public Set<String> getSelectedGroupingRules() {
    return mySelectedGroupingRules;
  }

  public void setSelectedGroupingRules(final Set<String> selectedGroupingRules) {
    mySelectedGroupingRules = selectedGroupingRules;
  }
}
