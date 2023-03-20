// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdleTracker;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.function.Supplier;

/**
 * Expands an action group for a warm-up as less intrusive as possible:
 * in an "idle" listener, on a UI-only data-context, with a dedicated action-place.
 */
public final class PopupMenuPreloader implements HierarchyListener {
  private static final Logger LOG = Logger.getInstance(PopupMenuPreloader.class);

  private static final String MODE = Registry.get("actionSystem.update.actions.preload.menus").getSelectedOption();
  private static final String PRELOADER_PLACE_SUFFIX = "(preload-" + MODE + ")";

  private static int ourEditorContextMenuPreloadCount;

  private final Supplier<? extends ActionGroup> myGroupSupplier;
  private final String myPlace;
  private final WeakReference<JComponent> myComponentRef;
  private final WeakReference<PopupHandler> myPopupHandlerRef;
  private final long myStarted = System.nanoTime();
  private int myRetries;
  private boolean myDisposed;

  private AccessToken removeIdleListener;

  public static void install(@NotNull JComponent component,
                             @NotNull String actionPlace,
                             @Nullable PopupHandler popupHandler,
                             @NotNull Supplier<? extends ActionGroup> groupSupplier) {
    if (ApplicationManager.getApplication().isUnitTestMode() || "none".equals(MODE)) {
      return;
    }
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
      preloader.removeIdleListener = IdleTracker.getInstance().addIdleListener(2_000, preloader::onIdle);
    };
    UiNotifyConnector.doWhenFirstShown(component, runnable);
    if (component instanceof JMenuBar) return;
    // second-time preloading for a hopefully non-trivial selection
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
    myPlace = actionPlace + PRELOADER_PLACE_SUFFIX;
    component.addHierarchyListener(this);
  }

  static boolean isToSkipComputeOnEDT(@NotNull String place) {
    return place.endsWith(PRELOADER_PLACE_SUFFIX) && "bgt".equals(MODE);
  }

  @Override
  public void hierarchyChanged(HierarchyEvent e) {
    LOG.assertTrue(!myDisposed, "already disposed");
    if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) <= 0) return;
    dispose(-1);
  }

  private void onIdle() {
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
    DataContext dataContext = Utils.freezeDataContext(
      Utils.wrapToAsyncDataContext(DataManager.getInstance().getDataContext(contextComponent)), null);
    long start = System.nanoTime();
    myRetries ++;
    CancellablePromise<List<AnAction>> promise = Utils.expandActionGroupAsync(
      actionGroup, new PresentationFactory(), dataContext, myPlace, false, true);
    promise.onSuccess(__ -> dispose(TimeoutUtil.getDurationMillis(start)));
    promise.onError(__ -> {
      int retries = Math.max(1, Registry.intValue("actionSystem.update.actions.max.retries", 20));
      if (myRetries > retries) {
        UIUtil.invokeLaterIfNeeded(() -> dispose(-1));
      }
    });
  }

  @RequiresEdt
  private void dispose(long millis) {
    if (myDisposed) {
      return;
    }

    myDisposed = true;
    if (removeIdleListener != null) {
      removeIdleListener.close();
      removeIdleListener = null;
    }

    JComponent component = myComponentRef.get();
    if (component != null) {
      component.removeHierarchyListener(this);
    }
    if (millis == -1) {
      return;
    }
    if (component instanceof EditorComponentImpl) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourEditorContextMenuPreloadCount ++;
    }
    ActionGroup group = myGroupSupplier.get();
    String text = group == null ? null : group.getTemplateText();
    LOG.info(TimeoutUtil.getDurationMillis(myStarted) + " ms since showing to preload popup menu " +
             (text == null ? "" : "'" + text + "' ") + "at '" + myPlace + "' in " + millis + " ms");
  }
}
