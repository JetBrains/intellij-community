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
package com.intellij.ui;

import java.awt.event.KeyEvent;

/**
 * User: lex
 * Date: Sep 18, 2003
 * Time: 5:40:20 PM
 */
public class CheckboxTree extends CheckboxTreeBase {

  public static abstract class CheckboxTreeCellRenderer extends CheckboxTreeCellRendererBase { // This is 6.0 compatibility layer 
    protected CheckboxTreeCellRenderer() {
    }

    protected CheckboxTreeCellRenderer(final boolean opaque) {
      super(opaque);
    }

    protected CheckboxTreeCellRenderer(boolean opaque, boolean usePartialStatusForParentNodes) {
      super(opaque, usePartialStatusForParentNodes);
    }
  }

  public CheckboxTree(final CheckboxTreeCellRenderer cellRenderer, CheckedTreeNode root) {
    super(cellRenderer, root);

    installSpeedSearch();
  }
  public CheckboxTree(final CheckboxTreeCellRenderer cellRenderer, CheckedTreeNode root, final CheckPolicy checkPolicy) {
    super(cellRenderer, root, checkPolicy);

    installSpeedSearch();
  }

  protected void installSpeedSearch() {
    new TreeSpeedSearch(this);
  }


  protected boolean isToggleEvent(KeyEvent e) {
    return super.isToggleEvent(e) &&  !SpeedSearchBase.hasActiveSpeedSearch(this);
  }


}
