package org.jetbrains.intellij.plugins.journey.diagram.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyNode;

import java.awt.*;
import java.util.Arrays;
import java.util.Objects;
import javax.swing.*;

import static org.jetbrains.intellij.plugins.journey.JourneyDataKeys.JOURNEY_DIAGRAM_DATA_MODEL;

public class JourneyTitleBar extends JPanel {
  private boolean expanded = false;
  private JBLabel titleLabel;

  public JourneyTitleBar(Editor editor, JourneyNode node) {
    super(new BorderLayout());

    titleLabel = new JBLabel("");
    titleLabel.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 0));
    titleLabel.setOpaque(true);
    titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
    add(titleLabel, BorderLayout.CENTER);

    ActionToolbar actionToolbar = initActionToolbar(editor, node);
    actionToolbar.setTargetComponent(editor.getComponent());
    actionToolbar.getComponent().setOpaque(true);
    add(actionToolbar.getComponent(), BorderLayout.EAST);
  }

  public void setTitle(@NlsContexts.Label String title) {
    titleLabel.setText(title);
  }

  private @NotNull ActionToolbar initActionToolbar(Editor editor, JourneyNode node) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    var icons = Arrays.asList(AllIcons.General.ExpandComponent, AllIcons.General.CollapseComponent);
    final var dataModel = Objects.requireNonNull(editor.getUserData(JOURNEY_DIAGRAM_DATA_MODEL));
    actionGroup.addAction(new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        expanded = !expanded;
        node.setFullViewState(expanded, dataModel);
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setIcon(icons.get(expanded ? 1 : 0));
      }
    });
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, actionGroup, true);
  }
}