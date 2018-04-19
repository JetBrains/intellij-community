// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.popup.list.ListPopupImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class TouchBarsManager {
  private final static boolean IS_LOGGING_ENABLED = false;
  private static final Logger LOG = Logger.getInstance(TouchBarsManager.class);
  private static final ArrayDeque<BarContainer> ourTouchBarStack = new ArrayDeque<>();
  private static TouchBar ourCurrentBar;

  public static void attachEditorBar(EditorEx editor) {
    if (!isTouchBarAvailable())
      return;

    final Project proj = editor.getProject();
    if (proj == null)
      return;

    editor.addFocusListener(new FocusChangeListener() {
      private BarContainer myEditorBar = ProjectBarsStorage.instance(proj).getBarContainer(ProjectBarsStorage.EDITOR);
      @Override
      public void focusGained(Editor editor) {
        if (!hasVisiblePopup())
          showTouchBar(myEditorBar);
      }
      @Override
      public void focusLost(Editor editor) {
        if (!hasVisiblePopup())
          closeTouchBar(myEditorBar);
      }
    });
  }

  public static void attachPopupBar(@NotNull ListPopupImpl listPopup) {
    if (!isTouchBarAvailable())
      return;

    listPopup.addPopupListener(new JBPopupListener() {
        BarContainer myPopupBar = new SingleBarContainer(()->_createScrubberBarFromPopup(listPopup)) {
          @Override
          public boolean isPopup() { return true; }
        };
        @Override
        public void beforeShown(LightweightWindowEvent event) {
          System.out.println("open popup: " + listPopup);
          showTouchBar(myPopupBar);
        }
        @Override
        public void onClosed(LightweightWindowEvent event) {
          System.out.println("closed popup: " + listPopup);
          closeTouchBar(myPopupBar);
          myPopupBar.release();
          myPopupBar = null;
        }
      }
    );
  }

  public static void initialize() {
    if (!isTouchBarAvailable())
      return;

    final ID app = Foundation.invoke("NSApplication", "sharedApplication");
    Foundation.invoke(app, "setAutomaticCustomizeTouchBarMenuItemEnabled:", true);

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(Project project) {
        trace("opened project %s, set general touchbar", project);
        showTouchBar(ProjectBarsStorage.instance(project).getBarContainer(ProjectBarsStorage.GENERAL));

        final ToolWindowManagerEx twm = ToolWindowManagerEx.getInstanceEx(project);
        twm.addToolWindowManagerListener(new ToolWindowManagerListener() {
          private BarContainer myDebuggerBar = ProjectBarsStorage.instance(project).getBarContainer(ProjectBarsStorage.DEBUGGER);

          @Override
          public void toolWindowRegistered(@NotNull String id) {}
          @Override
          public void stateChanged() {
            final String activeId = twm.getActiveToolWindowId();
            if (activeId != null && activeId.equals("Debug"))
              showTouchBar(myDebuggerBar);
            else
              closeTouchBar(myDebuggerBar);
          }
        });
      }
      @Override
      public void projectClosed(Project project) {
        trace("closed project %s, hide touchbar", project);
        closeTouchBar(ProjectBarsStorage.instance(project).getBarContainer(ProjectBarsStorage.GENERAL));
        ProjectBarsStorage.instance(project).releaseAll();
      }
    });
  }

  public static boolean isTouchBarAvailable() { return NST.isAvailable(); }

  public static void onKeyEvent(KeyEvent e) {
    if (!isTouchBarAvailable())
      return;

    if (
      e.getID() != KeyEvent.KEY_PRESSED
      && e.getID() != KeyEvent.KEY_RELEASED
    )
      return;

    if (
      e.getKeyCode() != KeyEvent.VK_CONTROL
      && e.getKeyCode() != KeyEvent.VK_ALT
      && e.getKeyCode() != KeyEvent.VK_META
      && e.getKeyCode() != KeyEvent.VK_SHIFT
    )
      return;

    long keymask = e.getModifiersEx();
    synchronized (TouchBarsManager.class) {
      for (BarContainer itb: ourTouchBarStack) {
        if (itb instanceof MultiBarContainer)
          ((MultiBarContainer)itb).selectBarByKeyMask(keymask);
      }

      _setTouchBar(ourTouchBarStack.peek());
    }
  }

  synchronized public static boolean hasVisiblePopup() {
    return ourTouchBarStack.stream().anyMatch((bc)->bc.isPopup());
  }

  synchronized public static void showTouchBar(@NotNull BarContainer bar) {
    final BarContainer top = ourTouchBarStack.peek();
    if (top == bar)
      return;

    ourTouchBarStack.remove(bar);
    ourTouchBarStack.push(bar);
    _setTouchBar(bar.get());
  }

  synchronized public static void closeTouchBar(@NotNull BarContainer tb) {
    if (ourTouchBarStack.isEmpty())
      return;

    BarContainer top = ourTouchBarStack.peek();
    if (top == tb) {
      ourTouchBarStack.pop();
      _setTouchBar(ourTouchBarStack.peek());
    } else {
      ourTouchBarStack.remove(tb);
    }
  }

  private static void _setTouchBar(BarContainer barProvider) { _setTouchBar(barProvider == null ? null : barProvider.get()); }

  private static void _setTouchBar(TouchBar bar) {
    if (ourCurrentBar == bar)
      return;

    ourCurrentBar = bar;
    NST.setTouchBar(bar);
  }

  private static void trace(String fmt, Object... args) {
    if (IS_LOGGING_ENABLED)
      LOG.trace(String.format(fmt, args));
  }

  private static TouchBar _createScrubberBarFromPopup(@NotNull ListPopupImpl listPopup) {
    final TouchBar result = new TouchBar("popup_scrubber_bar" + listPopup);

    List<TBItemScrubber.ItemData> items = new ArrayList<>();
    @NotNull ListPopupStep listPopupStep = listPopup.getListStep();
    for (Object obj: listPopupStep.getValues()) {
      final Icon ic = listPopupStep.getIconFor(obj);
      final String txt = listPopupStep.getTextFor(obj);

      final Runnable action = () -> {
        listPopup.getList().setSelectedValue(obj, false);
        listPopup.handleSelect(true);
      };

      items.add(new TBItemScrubber.ItemData(ic, txt, ()-> ApplicationManager.getApplication().invokeLater(() -> action.run())));
    }
    final TBItemScrubber scrub = result.addScrubber();
    scrub.setItems(items);

    result.selectVisibleItemsToShow();
    return result;
  }
}
