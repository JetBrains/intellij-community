package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 3/6/12 3:44 PM
 */
public class GradleProjectStructureFiltersPanel extends JPanel {
  

  public GradleProjectStructureFiltersPanel() {
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    add(Box.createHorizontalStrut(7));
    add(new JLabel(GradleBundle.message("gradle.import.structure.settings.label.filters")));
    final ActionManager actionManager = ActionManager.getInstance();
    final ActionGroup group = (ActionGroup)actionManager.getAction("Gradle.SyncTreeFilter");
    final ActionToolbar toolbar = actionManager.createActionToolbar(GradleConstants.SYNC_TREE_FILTER_PLACE, group, true);
    add(toolbar.getComponent());
  }
}
