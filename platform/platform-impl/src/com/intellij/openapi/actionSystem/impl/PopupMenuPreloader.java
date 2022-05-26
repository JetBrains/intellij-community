// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.IdeMenuBar;
import com.intellij.ui.PopupHandler;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.function.Supplier;

public final class PopupMenuPreloader implements Runnable {
  private static final Logger LOG = Logger.getInstance(PopupMenuPreloader.class);

  private static int ourEditorContextMenuPreloadCount;

  private final Supplier<? extends ActionGroup> myGroupSupplier;
  private final String myPlace;
  private final WeakReference<JComponent> myComponentRef;
  private final WeakReference<PopupHandler> myPopupHandlerRef;
  private int myRetries;
  private boolean myDisposed;

  public static void install(@NotNull JComponent component,
                             @NotNull String actionPlace,
                             @Nullable PopupHandler popupHandler,
                             @NotNull Supplier<? extends ActionGroup> groupSupplier) {
    if (component instanceof EditorComponentImpl && ourEditorContextMenuPreloadCount > 4 ||
        component instanceof IdeMenuBar && SwingUtilities.getWindowAncestor(component) instanceof IdeFrame.Child) {
      return;
    }
    Runnable runnable = () -> {
      if (popupHandler != null && !ArrayUtil.contains(popupHandler, component.getMouseListeners()) ||
          component instanceof EditorComponentImpl && !EditorUtil.isRealFileEditor(((EditorComponentImpl)component).getEditor())) {
        return;
      }
      PopupMenuPreloader preloader = new PopupMenuPreloader(component, actionPlace, popupHandler, groupSupplier);
      IdeEventQueue.getInstance().addIdleListener(preloader, 2000);
    };
    UiNotifyConnector.doWhenFirstShown(component, runnable);
    if (component instanceof JMenuBar) return;
    component.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        component.removeFocusListener(this);
        runnable.run();
      }
    });
  }

  private PopupMenuPreloader(@NotNull JComponent component,
                             @NotNull String actionPlace,
                             @Nullable PopupHandler popupHandler,
                             @NotNull Supplier<? extends ActionGroup> groupSupplier) {
    myComponentRef = new WeakReference<>(component);
    myPopupHandlerRef = popupHandler == null ? null : new WeakReference<>(popupHandler);

    myGroupSupplier = groupSupplier;
    myPlace = actionPlace;
  }

  @Override
  public void run() {
    JComponent component = myComponentRef.get();
    PopupHandler popupHandler = myPopupHandlerRef == null ? null : myPopupHandlerRef.get();
    if (component == null || !component.isShowing() ||
        myPopupHandlerRef != null && (popupHandler == null || !ArrayUtil.contains(popupHandler, component.getMouseListeners()))) {
      dispose(-1);
      return;
    }
    ActionGroup actionGroup = myGroupSupplier.get();
    if (actionGroup == null) {
      dispose(-1);
      return;
    }
    Component contextComponent = ActionPlaces.MAIN_MENU.equals(myPlace) ?
                                 IJSwingUtilities.getFocusedComponentInWindowOrSelf(component) : component;
    DataContext dataContext = Utils.wrapToAsyncDataContext(DataManager.getInstance().getDataContext(contextComponent));
    boolean isInModalContext = ModalityState.stateForComponent(component).dominates(ModalityState.NON_MODAL);
    long start = System.nanoTime();
    myRetries ++;
    CancellablePromise<List<AnAction>> promise = Utils.expandActionGroupAsync(
      isInModalContext, actionGroup, new PresentationFactory(), dataContext, myPlace);
    promise.onSuccess(__ -> dispose(TimeoutUtil.getDurationMillis(start)));
    promise.onError(__ -> {
      int retries = Math.max(1, Registry.intValue("actionSystem.update.actions.max.retries", 20));
      if (myRetries > retries) dispose(-1);
    });
  }

  private void dispose(long millis) {
    if (myDisposed) return;
    myDisposed = true;
    IdeEventQueue.getInstance().removeIdleListener(this);
    if (millis != -1) {
      if (myComponentRef.get() instanceof EditorComponentImpl) {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourEditorContextMenuPreloadCount ++;
      }
      ActionGroup group = myGroupSupplier.get();
      String text = group == null ? null : group.getTemplateText();
      LOG.info("Popup menu " + (text == null ? "" : "'" + text + "' ") + "preloaded at '" + myPlace + "' in " + millis + " ms");
    }
  }
}
