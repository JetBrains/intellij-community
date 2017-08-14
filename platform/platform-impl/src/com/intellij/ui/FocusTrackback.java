/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ExpirableRunnable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.FocusCommand;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;

public class FocusTrackback {

  private static final Logger LOG = Logger.getInstance("FocusTrackback");

  private static final Map<Window, List<FocusTrackback>> ourRootWindowToParentsStack = new WeakHashMap<>();
  private static final Map<Window, Component> ourRootWindowToFocusedMap = ContainerUtil.createWeakKeyWeakValueMap();

  private Window myParentWindow;

  private Window myRoot;

  private WeakReference<Component> myFocusOwner = new WeakReference<>(null);
  private WeakReference<Component> myLocalFocusOwner = new WeakReference<>(null);

  private final String myRequestorName;
  private ComponentQuery myFocusedComponentQuery;
  private boolean myMustBeShown;

  private boolean myConsumed;
  private final WeakReference myRequestor;
  private boolean myScheduledForRestore;
  private boolean myWillBeScheduledForRestore;
  private boolean myForcedRestore;

  public FocusTrackback(@NotNull Object requestor, Component parent, boolean mustBeShown) {
    this(requestor, parent == null ? null : UIUtil.getWindow(parent), mustBeShown);
  }

  public FocusTrackback(@NotNull Object requestor, Window parent, boolean mustBeShown) {
    myRequestor = new WeakReference<>(requestor);
    myRequestorName = requestor.toString();
    myParentWindow = parent;
    myMustBeShown = mustBeShown;


    if (isHeadlessOrWrongOS()) return;

    register(parent);

    final List<FocusTrackback> stack = getStackForRoot(myRoot);
    final int index = stack.indexOf(this);

    //todo [kirillk] diagnostics for IDEADEV-28766
    assert index >= 0 : myRequestorName;

    final KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    setLocalFocusOwner(manager.getPermanentFocusOwner());

    final IdeFocusManager fm = IdeFocusManager.getGlobalInstance();
    if (myLocalFocusOwner.get() == null && fm.isFocusBeingTransferred()) {
      if (index > 0) {
        int eachIndex = index - 1;
        while (eachIndex > 0) {
          final FocusTrackback each = stack.get(eachIndex);
          if (!each.isConsumed()) {
            Component component = each.myLocalFocusOwner.get();
            if (component != null) {
              setLocalFocusOwner(component);
              break;
            }
          }
          eachIndex--;
        }
      }
    }

    if (index == 0) {
      setFocusOwner(manager.getPermanentFocusOwner());
      if (getFocusOwner() == null) {
        final Window window = manager.getActiveWindow();
        if (window instanceof Provider) {
          final FocusTrackback other = ((Provider)window).getFocusTrackback();
          if (other != null) {
            setFocusOwner(other.getFocusOwner());
          }
        }
      }
    }
    else {
      setFocusOwner(stack.get(0).getFocusOwner());
    }

    if (stack.size() == 1 && getFocusOwner() == null) {
      setFocusOwner(getFocusFor(myRoot));
    }
    else if (index == 0 && getFocusOwner() != null) {
      setFocusFor(myRoot, getFocusOwner());
    }
  }

  private static boolean isHeadlessOrWrongOS() {
    Application app = ApplicationManager.getApplication();
    return app == null || app.isHeadlessEnvironment() || wrongOS();
  }

  private void setLocalFocusOwner(Component component) {
    myLocalFocusOwner = new WeakReference<>(component);
  }

  public static Component getFocusFor(Window parent) {
    return ourRootWindowToFocusedMap.get(parent);
  }

  private static void setFocusFor(Window parent, Component focus) {
    ourRootWindowToFocusedMap.put(parent, focus);
  }

  private static boolean wrongOS() {
    return false;
  }

  public void registerFocusComponent(@NotNull final Component focusedComponent) {
    registerFocusComponent(new ComponentQuery() {
      public Component getComponent() {
        return focusedComponent;
      }
    });
  }

  public void registerFocusComponent(@NotNull ComponentQuery query) {
    myFocusedComponentQuery = query;
  }

  private void register(final Window parent) {
    myRoot = findUltimateParent(parent);
    List<FocusTrackback> stack = getCleanStackForRoot();
    stack.remove(this);
    stack.add(this);
  }

  private List<FocusTrackback> getCleanStackForRoot() {
    return myRoot == null ? Collections.emptyList() : getCleanStackForRoot(myRoot);
  }

  private static List<FocusTrackback> getCleanStackForRoot(@NotNull Window root) {
    List<FocusTrackback> stack = getStackForRoot(root);

    final FocusTrackback[] stackArray = stack.toArray(new FocusTrackback[stack.size()]);
    for (FocusTrackback eachExisting : stackArray) {
      if (eachExisting != null && eachExisting.isConsumed()) {
        eachExisting.dispose();
      }
      else if (eachExisting == null) {
        stack.remove(eachExisting);
      }
    }
    return stack;
  }

  public void restoreFocus() {
    if (isHeadlessOrWrongOS() || myConsumed || isScheduledForRestore()) return;

    Project project = null;
    DataManager dataManager = DataManager.getInstance();
    if (dataManager != null) {
      DataContext context = myParentWindow == null ? dataManager.getDataContext() : dataManager.getDataContext(myParentWindow);
      if (context != null) {
        project = CommonDataKeys.PROJECT.getData(context);
      }
    }

    myScheduledForRestore = true;
    final List<FocusTrackback> stack = getCleanStackForRoot();
    final int index = stack.indexOf(this);
    for (int i = index - 1; i >=0; i--) {
      if (stack.get(i).isScheduledForRestore()) {
        dispose();
        return;
      }
    }

    if (project != null && !project.isDisposed()) {
      final IdeFocusManager focusManager = IdeFocusManager.getInstance(project);
      cleanParentWindow();
      final Project finalProject = project;
      focusManager.requestFocus(new MyFocusCommand(), myForcedRestore).doWhenProcessed(() -> dispose()).doWhenRejected(() -> focusManager.revalidateFocus(new ExpirableRunnable.ForProject(finalProject) {
        @Override
        public void run() {
          if (UIUtil.isMeaninglessFocusOwner(focusManager.getFocusOwner())) {
            focusManager.requestDefaultFocus(false);
          }
        }
      }));
    }
    else {
      // no ide focus manager, so no way -- do just later
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        _restoreFocus();
        dispose();
      });
    }
  }

  private ActionCallback _restoreFocus() {
    if (isConsumed()) return ActionCallback.REJECTED;

    List<FocusTrackback> stack = getCleanStack();

    if (!stack.contains(this)) return ActionCallback.REJECTED;

    Component toFocus = queryToFocus(stack, this, true);

    final ActionCallback result = new ActionCallback();
    if (toFocus != null) {
      final Component ownerBySwing = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      if (ownerBySwing != null) {
        final Window ownerBySwingWindow = SwingUtilities.getWindowAncestor(ownerBySwing);
        if (ownerBySwingWindow != null && ownerBySwingWindow == SwingUtilities.getWindowAncestor(toFocus)) {
          if (!UIUtil.isMeaninglessFocusOwner(ownerBySwing)) {
            toFocus = ownerBySwing;
          }
        }
      }

      if (myParentWindow != null) {
        final Window to = UIUtil.getWindow(toFocus);
        if (to != null && UIUtil.findUltimateParent(to) == UIUtil.findUltimateParent(myParentWindow)) {  // IDEADEV-34537
          requestFocus(toFocus);
          result.setDone();
        }
      } else {
        requestFocus(toFocus);
        result.setDone();
      }
    }

    if (!result.isDone()) {
      result.setRejected();
    }

    stack.remove(this);
    dispose();

    return result;
  }

  private void requestFocus(Component toFocus) {
    if (myForcedRestore) {
      toFocus.requestFocus();
    } else {
      toFocus.requestFocusInWindow();
    }
  }

  private static Component queryToFocus(final List<FocusTrackback> stack, final FocusTrackback trackback, boolean mustBeLastInStack) {
    final int index = stack.indexOf(trackback);
    Component toFocus = null;

    Component focusOwner = trackback.myLocalFocusOwner.get();
    if (focusOwner != null) {
      toFocus = focusOwner;

      if (UIUtil.isMeaninglessFocusOwner(toFocus)) {
        toFocus = null;
      }
    }

    if (toFocus == null) {
      if (index > 0) {
        final ComponentQuery query = stack.get(index - 1).myFocusedComponentQuery;
        toFocus = query != null ? query.getComponent() : null;
      }
      else {
        toFocus = trackback.getFocusOwner();
      }
    }

    if (mustBeLastInStack) {
      for (int i = index + 1; i < stack.size(); i++) {
        if (!stack.get(i).isMustBeShown()) {
          if ((stack.get(i).isScheduledForRestore() || stack.get(i).isWillBeScheduledForRestore()) && !stack.get(i).isConsumed()) {
            toFocus = null;
            break;
          }
        } else if (!stack.get(i).isConsumed()) {
          toFocus = null;
          break;
        }
      }
    }



    return toFocus;
  }

  private List<FocusTrackback> getCleanStack() {
    final List<FocusTrackback> stack = getStackForRoot(myRoot);

    final FocusTrackback[] all = stack.toArray(new FocusTrackback[stack.size()]);
    for (FocusTrackback each : all) {
      if (each == null || each != this && each.isConsumed()) {
        stack.remove(each);
      }
    }
    return stack;
  }

  private static List<FocusTrackback> getStackForRoot(@NotNull Window root) {
    List<FocusTrackback> stack = ourRootWindowToParentsStack.get(root);
    if (stack == null) {
      stack = new ArrayList<>();
      ourRootWindowToParentsStack.put(root, stack);
    }
    return stack;
  }

  @Nullable
  private static Window findUltimateParent(final Window parent) {
    Window root = parent == null ? JOptionPane.getRootFrame() : parent;
    while (root != null) {
      final Container next = root.getParent();
      if (next == null) break;
      if (next instanceof Window) {
        root = (Window)next;
      }
      final Window nextWindow = SwingUtilities.getWindowAncestor(next);
      if (nextWindow == null) break;
      root = nextWindow;
    }

    return root;
  }

  @Nullable
  public Component getFocusOwner() {
    return myFocusOwner.get();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return getClass().getName() + " requestor: " + myRequestorName + " parent=" + myParentWindow;
  }

  public void dispose() {
    if (myRoot == null) return;
    consume();
    getStackForRoot(myRoot).remove(this);
    myScheduledForRestore = false;

    if (myParentWindow != null) {
      FocusTraversalPolicy policy = myParentWindow.getFocusTraversalPolicy();
      if (policy instanceof LayoutFocusTraversalPolicyExt) {
        ((LayoutFocusTraversalPolicyExt)policy).setNoDefaultComponent(false, this);
      }
    }

    myParentWindow = null;
    myRoot = null;
    myFocusOwner.clear();
    myLocalFocusOwner.clear();
  }

  private boolean isConsumed() {
    if (myConsumed) return true;

    if (myMustBeShown) {
      return !isScheduledForRestore()
             && myFocusedComponentQuery != null
             && myFocusedComponentQuery.getComponent() != null
             && !myFocusedComponentQuery.getComponent().isShowing();
    }
    else {
      return myParentWindow == null || !myParentWindow.isShowing();
    }
  }

  public void consume() {
    myConsumed = true;
  }

  private void setFocusOwner(final Component focusOwner) {
    myFocusOwner = new WeakReference<>(focusOwner);
  }

  public void setMustBeShown(final boolean mustBeShown) {
    myMustBeShown = mustBeShown;
  }

  public boolean isMustBeShown() {
    return myMustBeShown;
  }

  public static void release(@NotNull final JFrame frame) {
    final Window[] all = ourRootWindowToParentsStack.keySet().toArray(new Window[ourRootWindowToParentsStack.size()]);
    for (Window each : all) {
      if (each == null) continue;

      if (each == frame || SwingUtilities.isDescendingFrom(each, frame)) {
        ourRootWindowToParentsStack.remove(each);
      }
    }

    ourRootWindowToFocusedMap.remove(frame);
  }

  public Object getRequestor() {
    return myRequestor.get();
  }

  public void setWillBeScheduledForRestore() {
    myWillBeScheduledForRestore = true;
  }

  public boolean isScheduledForRestore() {
    return myScheduledForRestore;
  }

  public boolean isWillBeScheduledForRestore() {
    return myWillBeScheduledForRestore;
  }

  public void setForcedRestore(boolean forcedRestore) {
    myForcedRestore = forcedRestore;
  }

  public void cleanParentWindow() {
    if (!Registry.is("focus.fix.lost.cursor")) return;
      if (myParentWindow != null) {
      try {
        Method tmpLost = Window.class.getDeclaredMethod("setTemporaryLostComponent", Component.class);
        tmpLost.setAccessible(true);
        tmpLost.invoke(myParentWindow, new Object[] {null});

        Method owner =
          KeyboardFocusManager.class.getDeclaredMethod("setMostRecentFocusOwner", Window.class, Component.class);
        owner.setAccessible(true);
        owner.invoke(null, myParentWindow, null);

        FocusTraversalPolicy policy = myParentWindow.getFocusTraversalPolicy();
        if (policy instanceof LayoutFocusTraversalPolicyExt) {
          ((LayoutFocusTraversalPolicyExt)policy).setNoDefaultComponent(true, this);
        }
      }
      catch (Exception e) {
        LOG.debug(e);
      }
    }
  }

  public interface Provider {
    FocusTrackback getFocusTrackback();
  }

  public interface ComponentQuery {
    Component getComponent();
  }

  @NotNull
  public static List<JBPopup> getChildPopups(@NotNull final Component component) {
    List<JBPopup> result = new ArrayList<>();

    final Window window = UIUtil.getWindow(component);
    if (window == null) return result;

    final List<FocusTrackback> stack = getCleanStackForRoot(findUltimateParent(window));

    for (FocusTrackback each : stack) {
      if (each.isChildFor(component) && each.getRequestor() instanceof JBPopup) {
        result.add((JBPopup)each.getRequestor());
      }
    }

    return result;
  }

  private boolean isChildFor(final Component parent) {
    final Component toFocus = queryToFocus(getCleanStack(), this, false);
    if (toFocus == null) return false;
    if (parent == toFocus) return true;

    if (SwingUtilities.isDescendingFrom(toFocus, parent)) return true;

    Component eachToFocus = getFocusOwner();
    FocusTrackback eachTrackback = this;
    while (true) {
      if (eachToFocus == null) {
        break;
      }
      if (SwingUtilities.isDescendingFrom(eachToFocus, parent)) return true;

      if (eachTrackback.getRequestor() instanceof AbstractPopup) {
        FocusTrackback newTrackback = ((AbstractPopup)eachTrackback.getRequestor()).getFocusTrackback();
        if (newTrackback == null || eachTrackback == newTrackback) break;
        if (eachTrackback == null || eachTrackback.isConsumed()) break;

        eachTrackback  = newTrackback;
        eachToFocus = eachTrackback.getFocusOwner();
      } else {
        break;
      }
    }

    return false;
  }


  private class MyFocusCommand extends FocusCommand {

    @NotNull
    public ActionCallback run() {
      return _restoreFocus();
    }

    @Override
    public boolean isExpired() {
      return isConsumed();
    }

    public String toString() {
      return "focus trackback requestor";
    }
  }
}
