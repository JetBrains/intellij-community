// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.FocusTraversalPolicy;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

public final class FocusUtil {
  private static final @NonNls String SWING_FOCUS_OWNER_PROPERTY = "focusOwner";
  private static final @NonNls String MANAGING_FOCUS_PROPERTY = "managingFocus";

  public static Component findFocusableComponentIn(Component searchIn, Component toSkip) {
    List<Component> components = UIUtil.uiTraverser(searchIn).toList();
    for (Component component : components) {
      if (component.equals(toSkip)) {
        continue;
      }
      if (component.isFocusable()) {
        return component;
      }
    }
    return searchIn;
  }

  public static @Nullable Component getMostRecentComponent(Component component, Window ancestor) {
    if (ancestor == null) {
      return null;
    }

    try {
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      MethodHandle owner =
        lookup.findStatic(KeyboardFocusManager.class, "getMostRecentFocusOwner", MethodType.methodType(Component.class, Window.class));
      Component mostRecentFocusOwner = (Component)owner.invokeExact(ancestor);
      if (mostRecentFocusOwner != null &&
          SwingUtilities.isDescendingFrom(mostRecentFocusOwner, component) &&
          mostRecentFocusOwner.isShowing()) {
        return mostRecentFocusOwner;
      }
    }
    catch (Throwable e) {
      Logger.getInstance(FocusUtil.class).debug(e);
    }
    return null;
  }

  public static Component getDefaultComponentInPanel(Component component) {
    if (!(component instanceof JPanel container)) {
      return null;
    }

    FocusTraversalPolicy policy = container.getFocusTraversalPolicy();
    if (policy == null) {
      return container;
    }

    final Component defaultComponent = policy.getDefaultComponent(container);
    if (defaultComponent == null) {
      return container;
    }
    return policy.getDefaultComponent(container);
  }

  /**
   * Adds {@code listener} to the {@code focusOwner} property of the current {@link KeyboardFocusManager}, until the
   * {@code parentDisposable} is disposed. The subscription follows the manager when the manager is replaced.
   */
  public static void addFocusOwnerListener(@NotNull Disposable parentDisposable, @NotNull PropertyChangeListener listener) {
    FocusOwnerSubscription subscription = new FocusOwnerSubscription(listener);
    Disposer.register(parentDisposable, subscription);
    subscription.moveTo(KeyboardFocusManager.getCurrentKeyboardFocusManager());
  }

  /**
   * A manager fires {@code focusOwner} only while it is the current manager, so a subscription that stays on a
   * replaced manager hears nothing more. A manager announces its own replacement with {@code managingFocus}. This
   * class then moves the subscription over and reports the focus owner of the new manager.
   */
  private static final class FocusOwnerSubscription implements PropertyChangeListener, Disposable {
    private final PropertyChangeListener myListener;
    private KeyboardFocusManager myManager;
    private boolean myDisposed;

    FocusOwnerSubscription(@NotNull PropertyChangeListener listener) {
      myListener = listener;
    }

    @Override
    public void propertyChange(@NotNull PropertyChangeEvent event) {
      String propertyName = event.getPropertyName();
      if (SWING_FOCUS_OWNER_PROPERTY.equals(propertyName)) {
        myListener.propertyChange(event);
      } else if (MANAGING_FOCUS_PROPERTY.equals(propertyName) && Boolean.FALSE.equals(event.getNewValue())) {
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        if (moveTo(manager)) {
          PropertyChangeEvent changeEvent = new PropertyChangeEvent(
            manager,
            SWING_FOCUS_OWNER_PROPERTY,
            null,
            manager.getFocusOwner()
          );
          myListener.propertyChange(changeEvent);
        }
      }
    }

    @Override
    public synchronized void dispose() {
      myDisposed = true;
      unsubscribe();
    }

    /**
     * @return whether the subscription moved, which is {@code false} for the manager it already listens to
     */
    private synchronized boolean moveTo(@NotNull KeyboardFocusManager manager) {
      if (myDisposed || manager == myManager) {
        return false;
      }
      unsubscribe();
      myManager = manager;
      manager.addPropertyChangeListener(SWING_FOCUS_OWNER_PROPERTY, this);
      manager.addPropertyChangeListener(MANAGING_FOCUS_PROPERTY, this);
      return true;
    }

    private void unsubscribe() {
      if (myManager != null) {
        myManager.removePropertyChangeListener(SWING_FOCUS_OWNER_PROPERTY, this);
        myManager.removePropertyChangeListener(MANAGING_FOCUS_PROPERTY, this);
        myManager = null;
      }
    }
  }
}
