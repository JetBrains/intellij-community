// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Key;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.function.Function;

import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT;

public class SwingActionDelegate extends AnAction implements ActionRemoteBehaviorSpecification.Frontend, DumbAware {
  private static final Key<Function<String, JComponent>> FUNCTION = Key.create("SwingActionsMapping");
  private final String mySwingActionId;

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  protected SwingActionDelegate(String actionId) {
    setEnabledInModalContext(true);
    mySwingActionId = actionId;
  }

  protected @Nullable JComponent getComponent(AnActionEvent event) {
    JComponent component = ComponentUtil.getParentOfType(JComponent.class, event.getData(CONTEXT_COMPONENT));
    Function<? super String, JComponent> function = component == null ? null : ComponentUtil.getClientProperty(component, FUNCTION);
    return function == null ? component : function.apply(mySwingActionId);
  }

  protected static boolean speedSearchHandlesNavigation(@NotNull JComponent component) {
    var speedSearch = SpeedSearchSupply.getSupply(component);
    return speedSearch != null && speedSearch.supportsNavigation();
  }

  @Override
  public final void update(@NotNull AnActionEvent event) {
    event.getPresentation().setEnabled(null != getAction(mySwingActionId, getComponent(event)));
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent event) {
    performAction(mySwingActionId, getComponent(event));
  }

  /**
   * @param id        a string identifier of an action to perform
   * @param component a Swing component that provides an action map
   * @return {@code true} if action is performed, {@code false} otherwise
   */
  public static boolean performAction(@Nullable String id, @Nullable JComponent component) {
    Action action = getAction(id, component);
    if (action == null) return false;
    action.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, id));
    return true;
  }

  /**
   * @param id        a string identifier of an action to find
   * @param component a Swing component that provides an action map
   * @return {@code null} if the given component does not provide the specified action
   */
  public static @Nullable Action getAction(@Nullable String id, @Nullable JComponent component) {
    if (id == null || component == null) return null;
    ActionMap map = component.getActionMap();
    return map == null ? null : map.get(id);
  }

  /**
   * @param component the base component that delegates performing of actions to a dependant component
   * @param mapping   a function that returns a component that able to perform a named action,
   *                  or {@code null} to remove mapping for the given base component
   */
  @ApiStatus.Experimental
  public static void configureMapping(@NotNull JComponent component, @Nullable Function<? super String, JComponent> mapping) {
    component.putClientProperty(FUNCTION, mapping);
  }

  /**
   * @param base      the base component that delegates performing of actions to the dependant component
   * @param dependant the dependant component that should perform supported actions instead of the base component
   * @param actions   a list of supported actions
   */
  @ApiStatus.Experimental
  public static void configureMapping(@NotNull JComponent base, @NotNull JComponent dependant, String @NotNull ... actions) {
    HashMap<String, JComponent> map = new HashMap<>();
    for (String action : actions) map.put(action, dependant);
    configureMapping(base, map::get);
  }

  /**
   * @param component the base component that should not perform actions
   */
  @ApiStatus.Experimental
  public static void disableFor(@NotNull JComponent component) {
    configureMapping(component, action -> null);
  }
}