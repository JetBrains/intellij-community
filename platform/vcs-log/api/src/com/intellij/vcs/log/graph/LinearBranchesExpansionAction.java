/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.graph;

public class LinearBranchesExpansionAction implements GraphAction {

  public static LinearBranchesExpansionAction EXPAND = new LinearBranchesExpansionAction(true);
  public static LinearBranchesExpansionAction COLLAPSE = new LinearBranchesExpansionAction(false);

  private final boolean myExpand;

  private LinearBranchesExpansionAction(boolean expand) {
    myExpand = expand;
  }

  public boolean shouldExpand() {
    return myExpand;
  }
}
