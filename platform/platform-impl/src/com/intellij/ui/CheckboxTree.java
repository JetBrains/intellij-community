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
  }

  public CheckboxTree(final CheckboxTreeCellRenderer cellRenderer, CheckedTreeNode root) {
    super(cellRenderer, root);

    TreeToolTipHandler.install(this);
    installSpeedSearch();    
  }
  public CheckboxTree(final CheckboxTreeCellRenderer cellRenderer, CheckedTreeNode root, final CheckPolicy checkPolicy) {
    super(cellRenderer, root, checkPolicy);

    TreeToolTipHandler.install(this);
    installSpeedSearch();
  }

  protected void installSpeedSearch() {
    new TreeSpeedSearch(this);
  }


  protected boolean isToggleEvent(KeyEvent e) {
    return super.isToggleEvent(e) &&  !SpeedSearchBase.hasActiveSpeedSearch(this);
  }


}
