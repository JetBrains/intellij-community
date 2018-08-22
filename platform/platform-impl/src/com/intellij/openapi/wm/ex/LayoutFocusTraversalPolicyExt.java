// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
public class LayoutFocusTraversalPolicyExt extends LayoutFocusTraversalPolicy{
  /**
   * Overriden default component
   */
  private static JComponent myOverridenDefaultComponent;

  private boolean myNoDefaultComponent;
  private Object myNoDefaultComponentRequestor;

  private boolean myQueryImpl = false;

  public void setNoDefaultComponent(boolean noDefaultComponent, Object requestor) {
    if (noDefaultComponent) {
      myNoDefaultComponent = noDefaultComponent;
      myNoDefaultComponentRequestor = requestor;
    } else {
      if (myNoDefaultComponentRequestor == requestor) {
        myNoDefaultComponent = false;
        myNoDefaultComponentRequestor = null;
      }
    }
  }

  public boolean isNoDefaultComponent() {
    if (myQueryImpl) return false;

    return myNoDefaultComponent || Registry.is("actionSystem.noDefaultComponent");
  }

  @Nullable
  public static LayoutFocusTraversalPolicyExt findWindowPolicy(Component c) {
    Window wnd = UIUtil.getWindow(c);

    final FocusTraversalPolicy policy = wnd.getFocusTraversalPolicy();
    if (policy instanceof LayoutFocusTraversalPolicyExt) {
      return (LayoutFocusTraversalPolicyExt)policy;
    } else {
      return null;
    }
  }

  /**
   * !!!!! CAUTION !!!!!
   * !!!!! CAUTION !!!!!
   * !!!!! CAUTION !!!!!
   *
   * THIS IS AN "ABSOLUTELY-GURU METHOD".
   * NOBODY SHOULD ADD OTHER USAGES OF IT :)
   * ONLY ANTON AND VOVA ARE PERMITTED TO USE THIS METHOD!!!
   *
   * !!!!! CAUTION !!!!!
   * !!!!! CAUTION !!!!!
   * !!!!! CAUTION !!!!!
   *
   * This is absolutely guru method. Please do not use it without deep understanding
   * of Swing concepts, especially Swing focus management. <b>do not forget to clear
   * this "overiden" component!</b>
   */
  public static void setOverridenDefaultComponent(final JComponent overridenDefaultComponent) {
    myOverridenDefaultComponent = overridenDefaultComponent;
  }

  @Override
  public final Component getDefaultComponent(final Container focusCycleRoot) {
    if (isNoDefaultComponent()) return null;

    if (myOverridenDefaultComponent != null) {
      return myOverridenDefaultComponent;
    }
    return getDefaultComponentImpl(focusCycleRoot);
  }

  protected Component getDefaultComponentImpl(final Container focusCycleRoot) {
    return super.getDefaultComponent(focusCycleRoot);
  }

  @Override
  public Component getFirstComponent(final Container focusCycleRoot) {
    if (myOverridenDefaultComponent != null) {
      return myOverridenDefaultComponent;
    }
    return getFirstComponentImpl(focusCycleRoot);
  }

  protected Component getFirstComponentImpl(final Container focusCycleRoot) {
    return super.getFirstComponent(focusCycleRoot);
  }

  @Override
  public Component getLastComponent(final Container focusCycleRoot) {
    if (myOverridenDefaultComponent != null) {
      return myOverridenDefaultComponent;
    }
    return getLastComponentImpl(focusCycleRoot);
  }

  protected Component getLastComponentImpl(final Container focusCycleRoot) {
    return super.getLastComponent(focusCycleRoot);
  }

  @Override
  public Component getComponentAfter(final Container focusCycleRoot, final Component aComponent) {
    if (myOverridenDefaultComponent != null) {
      return myOverridenDefaultComponent;
    }
    return getComponentAfterImpl(focusCycleRoot, aComponent);
  }

  protected Component getComponentAfterImpl(final Container focusCycleRoot, final Component aComponent) {
    return super.getComponentAfter(focusCycleRoot, aComponent);
  }

  @Override
  public Component getComponentBefore(final Container focusCycleRoot, final Component aComponent) {
    if (myOverridenDefaultComponent != null) {
      return myOverridenDefaultComponent;
    }
    return getComponentBeforeImpl(focusCycleRoot, aComponent);
  }

  @Override
  public Component getInitialComponent(JInternalFrame frame) {
    if (isNoDefaultComponent()) return null;

    return super.getInitialComponent(frame);
  }

  @Override
  public Component getInitialComponent(Window window) {
    if (isNoDefaultComponent()) return null;

    return super.getInitialComponent(window);
  }

  protected Component getComponentBeforeImpl(final Container focusCycleRoot, final Component aComponent) {
    return super.getComponentBefore(focusCycleRoot, aComponent);
  }

  public Component queryImpl(Computable<Component> runnable) {
    try {
      myQueryImpl = true;
      return runnable.compute();
    }
    finally {
      myQueryImpl = false;
    }
  }
}
