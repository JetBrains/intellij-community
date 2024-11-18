package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import javax.swing.*;
import javax.swing.border.Border;

public class JourneyTitleBar extends JBPanel<JBPanel> {
  public JourneyTitleBar(@NlsContexts.Label String title, JComponent component) {
    super(new BorderLayout());

    DefaultActionGroup actionGroup = new DefaultActionGroup();
    var icons = Arrays.asList(AllIcons.General.ExpandComponent, AllIcons.General.Close);
    for (int i = 0; i < icons.size(); i++) {
      final Icon icon = icons.get(i);
      actionGroup.addAction(new AnAction() {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
          }
          @Override
          public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setIcon(icon);
          }
        }
      );
    }
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(
      ActionPlaces.TOOLBAR, actionGroup, true
    );
    actionToolbar.setTargetComponent(component);
    actionToolbar.getComponent().setOpaque(true);

    JBLabel titleLabel = new JBLabel(title);
    Border offsetBorder = BorderFactory.createEmptyBorder(0, 5, 0, 0);
    titleLabel.setBorder(offsetBorder);
    titleLabel.setOpaque(true);
    add(titleLabel, BorderLayout.CENTER);
    add(actionToolbar.getComponent(), BorderLayout.EAST);
  }
}