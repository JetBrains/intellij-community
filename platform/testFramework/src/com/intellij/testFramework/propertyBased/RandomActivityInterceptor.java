// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.propertyBased;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.ui.UiInterceptors;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.ImperativeCommand;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A UI Interceptor which intercepts every known UI component shown and performs random activity within it
 * logging that activity.
 */
public class RandomActivityInterceptor extends UiInterceptors.UiInterceptor<Object> {
  /**
   * A sentinel UI component which is activated at dispose to remove the last interceptor
   * from an interception queue.
   */
  private static final Object SINK = ObjectUtils.sentinel("RandomActivityInterceptor.SINK");
  private final ImperativeCommand.Environment myEnvironment;

  public RandomActivityInterceptor(ImperativeCommand.Environment env, Disposable disposable) {
    super(Object.class);
    myEnvironment = env;
    Disposer.register(disposable, () -> UiInterceptors.tryIntercept(SINK));
  }

  @Override
  protected void doIntercept(@NotNull Object component) {
    if (component == SINK) {
      return;
    }
    // Re-register this interceptor to be able to intercept one more UI component if any
    UiInterceptors.register(this);
    if (component instanceof JBPopup popup) {

      // effective there is a chain of 3 nonBlockingRead actions
      for (int i = 0; i < 3; i++) {
        JBLoadingPanel loadingPanel = popup.isDisposed() ? null : UIUtil.findComponentOfType(popup.getContent(), JBLoadingPanel.class);
        if (loadingPanel == null) break;
        UIUtil.dispatchAllInvocationEvents();
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      }

      JBList<?> content = popup.isDisposed() ? null : UIUtil.findComponentOfType(popup.getContent(), JBList.class);
      if (content == null) {
        fail("JBList not found under " + popup.getContent());
      }
      int count = content.getItemsCount();
      int index = myEnvironment.generateValue(Generator.integers(0, count - 1), null);
      Object selectedObject = content.getModel().getElementAt(index);
      myEnvironment.logMessage("Selected item '"+selectedObject+"' from popup");
      content.setSelectedIndex(index);
      assertTrue(popup.canClose()); // calls cancelHandler
      popup.closeOk(null);
    } else if (component instanceof ConflictsDialog) {
      // Do nothing, just ignore conflicts
    } else if (component instanceof DialogWrapper dialogWrapper) {
      dialogWrapper.performOKAction();
    } else {
      throw new UnsupportedOperationException(
        String.format("Cannot intercept UI component %s (class: %s)", component, component.getClass()));
    }
  }
}
