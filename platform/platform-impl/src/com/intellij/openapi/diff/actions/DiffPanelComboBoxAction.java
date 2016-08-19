package com.intellij.openapi.diff.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.ex.DiffPanelEx;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public abstract class DiffPanelComboBoxAction<T> extends ComboBoxAction implements DumbAware {
  @NotNull private final Map<T, AnAction> myActions = new HashMap<>();
  @NotNull private final T[] myActionOrder;

  protected DiffPanelComboBoxAction(@NotNull T[] actionOrder) {
    myActionOrder = actionOrder;
  }

  @NotNull
  protected abstract String getActionName();

  @NotNull
  protected abstract T getCurrentOption(@NotNull DiffPanelEx diffPanel);

  @Nullable
  private static DiffPanelEx getDiffPanel(@NotNull DataContext context) {
    return DiffPanelImpl.fromDataContext(context);
  }

  protected void addAction(T key, @NotNull AnAction action) {
    myActions.put(key, action);
  }

  @Override
  public JComponent createCustomComponent(final Presentation presentation) {
    JPanel panel = new JPanel(new BorderLayout());
    final JLabel label = new JLabel(getActionName());
    label.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
    panel.add(label, BorderLayout.WEST);
    panel.add(super.createCustomComponent(presentation), BorderLayout.CENTER);
    return panel;
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    for (T option : myActionOrder) {
      actionGroup.add(myActions.get(option));
    }
    return actionGroup;
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    DiffPanelEx diffPanel = getDiffPanel(e.getDataContext());
    if (diffPanel != null && diffPanel.getComponent().isDisplayable()) {
      AnAction action = myActions.get(getCurrentOption(diffPanel));
      Presentation templatePresentation = action.getTemplatePresentation();
      presentation.setIcon(templatePresentation.getIcon());
      presentation.setText(templatePresentation.getText());
      presentation.setEnabled(true);
    }
    else {
      presentation.setIcon(null);
      presentation.setText(DiffBundle.message("diff.panel.combo.box.action.not.available.action.name"));
      presentation.setEnabled(false);
    }
  }

  protected static abstract class DiffPanelAction extends DumbAwareAction {
    public DiffPanelAction(@NotNull String text) {
      super(text);
    }

    public void actionPerformed(AnActionEvent e) {
      final DiffPanelEx diffPanel = getDiffPanel(e.getDataContext());
      if (diffPanel != null) {
        perform(diffPanel);
      }
    }

    protected abstract void perform(@NotNull DiffPanelEx diffPanel);
  }
}
