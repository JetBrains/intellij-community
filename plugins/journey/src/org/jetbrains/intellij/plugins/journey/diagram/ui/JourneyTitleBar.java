package org.jetbrains.intellij.plugins.journey.diagram.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.Arrays;
import javax.swing.*;

public class JourneyTitleBar extends JBPanel<JBPanel> {
  public JourneyTitleBar(@NlsContexts.Label String title, Editor editor, List<Runnable> runnables) {
    super(new BorderLayout());

    JBLabel titleLabel = new JBLabel(title);
    titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 0));
    titleLabel.setOpaque(true);
    titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
    add(titleLabel, BorderLayout.CENTER);

    ActionToolbar actionToolbar = initActionToolbar(runnables);
    actionToolbar.setTargetComponent(editor.getComponent());
    actionToolbar.getComponent().setOpaque(true);
    add(actionToolbar.getComponent(), BorderLayout.EAST);
  }

  private @NotNull ActionToolbar initActionToolbar(List<Runnable> runnables) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    var icons = Arrays.asList(AllIcons.General.HideToolWindow, AllIcons.General.ExpandComponent, AllIcons.General.Close);
    for (int i = 0; i < icons.size(); i++) {
      int finalI = i;
      actionGroup.addAction(new AnAction() {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            runnables.get(finalI).run();
          }

          @Override
          public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setIcon(icons.get(finalI));
          }
        }
      );
    }
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, actionGroup, true);
  }
}