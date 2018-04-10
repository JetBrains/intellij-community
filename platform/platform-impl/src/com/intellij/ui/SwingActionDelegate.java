package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JComponent;
import java.awt.event.ActionEvent;

import static com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT;
import static com.intellij.util.ui.UIUtil.getParentOfType;

/**
 * @author Sergey.Malenkov
 */
public class SwingActionDelegate extends AnAction {
  private final String mySwingActionId;

  protected SwingActionDelegate(String actionId) {
    setEnabledInModalContext(true);
    mySwingActionId = actionId;
  }

  protected JComponent getComponent(AnActionEvent event) {
    return getParentOfType(JComponent.class, event.getData(CONTEXT_COMPONENT));
  }

  @Override
  public final void update(AnActionEvent event) {
    event.getPresentation().setEnabled(null != getSwingAction(getComponent(event)));
  }

  @Override
  public final void actionPerformed(AnActionEvent event) {
    JComponent component = getComponent(event);
    Action action = getSwingAction(component);
    if (action != null) action.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, mySwingActionId));
  }

  private Action getSwingAction(JComponent component) {
    if (component == null) return null;
    ActionMap map = component.getActionMap();
    return map == null ? null : map.get(mySwingActionId);
  }
}
