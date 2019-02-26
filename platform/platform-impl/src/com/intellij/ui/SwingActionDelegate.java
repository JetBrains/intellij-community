package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JComponent;
import java.awt.event.ActionEvent;

import static com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT;
import static com.intellij.util.ui.UIUtil.getParentOfType;

/**
 * @author Sergey.Malenkov
 */
public class SwingActionDelegate extends AnAction implements DumbAware {
  private static final Object DISABLED = new Object();
  private final String mySwingActionId;

  protected SwingActionDelegate(String actionId) {
    setEnabledInModalContext(true);
    mySwingActionId = actionId;
  }

  protected JComponent getComponent(AnActionEvent event) {
    return getParentOfType(JComponent.class, event.getData(CONTEXT_COMPONENT));
  }

  @Override
  public final void update(@NotNull AnActionEvent event) {
    event.getPresentation().setEnabled(null != getSwingAction(getComponent(event)));
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent event) {
    JComponent component = getComponent(event);
    Action action = getSwingAction(component);
    if (action != null) action.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, mySwingActionId));
  }

  private Action getSwingAction(JComponent component) {
    if (component == null) return null;
    if (Boolean.TRUE.equals(component.getClientProperty(DISABLED))) return null;
    ActionMap map = component.getActionMap();
    return map == null ? null : map.get(mySwingActionId);
  }

  @ApiStatus.Experimental
  public static void disableFor(@NotNull JComponent component) {
    component.putClientProperty(DISABLED, true);
  }
}
