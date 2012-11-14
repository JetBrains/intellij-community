/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
*/
@Tag("breakpoints-dialog")
public class XBreakpointsDialogState {
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
