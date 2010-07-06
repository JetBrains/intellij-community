/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Vladimir Kondratyev
 */
public final class DesktopLayout implements JDOMExternalizable {
  @NonNls static final String TAG = "layout";
  /**
   * Map between <code>id</code>s and registered <code>WindowInfo</code>s.
   */
  private final HashMap<String, WindowInfoImpl> myRegisteredId2Info;
  /**
   * Map between <code>id</code>s and unregistered <code>WindowInfo</code>s.
   */
  private final HashMap<String, WindowInfoImpl> myUnregisteredId2Info;
  /**
   *
   */
  private static final MyWindowInfoComparator ourWindowInfoComparator = new MyWindowInfoComparator();
  /**
   * Don't use this member directly. Get it only by <code>getInfos</code> method.
   * It exists here only for optimization purposes. This member can be <code>null</code>
   * if the cached data is invalid.
   */
  private WindowInfoImpl[] myRegisteredInfos;
  /**
   * Don't use this member directly. Get it only by <code>getUnregisteredInfos</code> method.
   * It exists here only for optimization purposes. This member can be <code>null</code>
   * if the cached data is invalid.
   */
  private WindowInfoImpl[] myUnregisteredInfos;
  /**
   * Don't use this member directly. Get it only by <code>getAllInfos</code> method.
   * It exists here only for optimization purposes. This member can be <code>null</code>
   * if the cached data is invalid.
   */
  private WindowInfoImpl[] myAllInfos;
  @NonNls public static final String ID_ATTR = "id";

  public DesktopLayout() {
    myRegisteredId2Info = new HashMap<String, WindowInfoImpl>();
    myUnregisteredId2Info = new HashMap<String, WindowInfoImpl>();
  }

  /**
   * Copies itself from the passed
   *
   * @param layout to be copied.
   */
  public final void copyFrom(final DesktopLayout layout) {
    final WindowInfoImpl[] infos = layout.getAllInfos();
    for (WindowInfoImpl info1 : infos) {
      WindowInfoImpl info = myRegisteredId2Info.get(info1.getId());
      if (info != null) {
        info.copyFrom(info1);
        continue;
      }
      info = myUnregisteredId2Info.get(info1.getId());
      if (info != null) {
        info.copyFrom(info1);
      }
      else {
        myUnregisteredId2Info.put(info1.getId(), info1.copy());
      }
    }
    // invalidate caches
    myRegisteredInfos = null;
    myUnregisteredInfos = null;
    myAllInfos = null;
    // normalize orders
    normalizeOrder(getAllInfos(ToolWindowAnchor.TOP));
    normalizeOrder(getAllInfos(ToolWindowAnchor.LEFT));
    normalizeOrder(getAllInfos(ToolWindowAnchor.BOTTOM));
    normalizeOrder(getAllInfos(ToolWindowAnchor.RIGHT));
  }

  /**
   * Creates or gets <code>WindowInfo</code> for the specified <code>id</code>. If tool
   * window is being registered first time the method uses <code>anchor</code>.
   *
   * @param id     <code>id</code> of tool window to be registered.
   * @param anchor the default tool window anchor.
   * @return
   */
  final WindowInfoImpl register(final String id, final ToolWindowAnchor anchor, final boolean splitMode) {
    WindowInfoImpl info = myUnregisteredId2Info.get(id);
    if (info != null) { // tool window has been already registered some time
      myUnregisteredId2Info.remove(id);
    }
    else { // tool window is being registered first time
      info = new WindowInfoImpl(id);
      info.setAnchor(anchor);
      info.setSplit(splitMode);
    }
    myRegisteredId2Info.put(id, info);
    // invalidate caches
    myRegisteredInfos = null;
    myUnregisteredInfos = null;
    myAllInfos = null;
    //
    return info;
  }

  final void unregister(final String id) {
    final WindowInfoImpl info = myRegisteredId2Info.remove(id).copy();
    myUnregisteredId2Info.put(id, info);
    // invalidate caches
    myRegisteredInfos = null;
    myUnregisteredInfos = null;
    myAllInfos = null;
  }

  /**
   * @return <code>WindowInfo</code> for the window with specified <code>id</code>.
   *         If <code>onlyRegistered</code> is <code>true</code> then returns not <code>null</code>
   *         value if and only if window with <code>id</code> is registered one.
   */
  final WindowInfoImpl getInfo(final String id, final boolean onlyRegistered) {
    final WindowInfoImpl info = myRegisteredId2Info.get(id);
    if (onlyRegistered || info != null) {
      return info;
    }
    else {
      return myUnregisteredId2Info.get(id);
    }
  }

  @Nullable
  final String getActiveId() {
    final WindowInfoImpl[] infos = getInfos();
    for (WindowInfoImpl info : infos) {
      if (info.isActive()) {
        return info.getId();
      }
    }
    return null;
  }

  /**
   * @return <code>WindowInfo</code>s for all registered tool windows.
   */
  final WindowInfoImpl[] getInfos() {
    if (myRegisteredInfos == null) {
      myRegisteredInfos = myRegisteredId2Info.values().toArray(new WindowInfoImpl[myRegisteredId2Info.size()]);
    }
    return myRegisteredInfos;
  }

  /**
   * @return <code>WindowInfos</code>s for all windows that are currently unregistered.
   */
  private WindowInfoImpl[] getUnregisteredInfos() {
    if (myUnregisteredInfos == null) {
      myUnregisteredInfos = myUnregisteredId2Info.values().toArray(new WindowInfoImpl[myUnregisteredId2Info.size()]);
    }
    return myUnregisteredInfos;
  }

  /**
   * @return <code>WindowInfo</code>s of all (registered and unregistered) tool windows.
   */
  WindowInfoImpl[] getAllInfos() {
    final WindowInfoImpl[] registeredInfos = getInfos();
    final WindowInfoImpl[] unregisteredInfos = getUnregisteredInfos();
    myAllInfos = ArrayUtil.mergeArrays(registeredInfos, unregisteredInfos, WindowInfoImpl.class);
    return myAllInfos;
  }

  /**
   * @return all (registered and not unregistered) <code>WindowInfos</code> for the specified <code>anchor</code>.
   *         Returned infos are sorted by order.
   */
  private WindowInfoImpl[] getAllInfos(final ToolWindowAnchor anchor) {
    WindowInfoImpl[] infos = getAllInfos();
    final ArrayList<WindowInfoImpl> list = new ArrayList<WindowInfoImpl>(infos.length);
    for (WindowInfoImpl info : infos) {
      if (anchor == info.getAnchor()) {
        list.add(info);
      }
    }
    infos = list.toArray(new WindowInfoImpl[list.size()]);
    Arrays.sort(infos, ourWindowInfoComparator);
    return infos;
  }

  /**
   * Normalizes order of windows in the passed array. Note, that array should be
   * sorted by order (by ascending). Order of first window will be <code>0</code>.
   */
  private static void normalizeOrder(final WindowInfoImpl[] infos) {
    for (int i = 0; i < infos.length; i++) {
      infos[i].setOrder(i);
    }
  }

  final boolean isToolWindowRegistered(final String id) {
    return myRegisteredId2Info.containsKey(id);
  }

  /**
   * @return comparator which compares <code>StripeButtons</code> in the stripe with
   *         specified <code>anchor</code>.
   */
  final Comparator<StripeButton> comparator(final ToolWindowAnchor anchor) {
    return new MyStripeButtonComparator(anchor);
  }

  /**
   * @param anchor anchor of the stripe.
   * @return maximum ordinal number in the specified stripe. Returns <code>-1</code>
   *         if there is no any tool window with the specified anchor.
   */
  private int getMaxOrder(final ToolWindowAnchor anchor) {
    int res = -1;
    final WindowInfoImpl[] infos = getAllInfos();
    for (final WindowInfoImpl info : infos) {
      if (anchor == info.getAnchor() && res < info.getOrder()) {
        res = info.getOrder();
      }
    }
    return res;
  }

  /**
   * Sets new <code>anchor</code> and <code>id</code> for the specified tool window.
   * Also the method properly updates order of all other tool windows.
   *
   * @param newAnchor new anchor
   * @param newOrder  new order
   */
  final void setAnchor(final String id, final ToolWindowAnchor newAnchor, int newOrder) {
    if (newOrder == -1) { // if order isn't defined then the window will the last in the stripe
      newOrder = getMaxOrder(newAnchor) + 1;
    }
    final WindowInfoImpl info = getInfo(id, true);
    final ToolWindowAnchor oldAnchor = info.getAnchor();
    // Shift order to the right in the target stripe.
    final WindowInfoImpl[] infos = getAllInfos(newAnchor);
    for (int i = infos.length - 1; i > -1; i--) {
      final WindowInfoImpl info2 = infos[i];
      if (newOrder <= info2.getOrder()) {
        info2.setOrder(info2.getOrder() + 1);
      }
    }
    // "move" window into the target position
    info.setAnchor(newAnchor);
    info.setOrder(newOrder);
    // Normalize orders in the source and target stripes
    normalizeOrder(getAllInfos(oldAnchor));
    if (oldAnchor != newAnchor) {
      normalizeOrder(getAllInfos(newAnchor));
    }
  }

  final void setSplitMode(final String id, boolean split) {
    final WindowInfoImpl info = getInfo(id, true);
    info.setSplit(split);
  }

  public final void readExternal(final Element layoutElement) {
    for (Object o : layoutElement.getChildren()) {
      final Element e = (Element)o;
      if (WindowInfoImpl.TAG.equals(e.getName())) {
        final WindowInfoImpl info = new WindowInfoImpl(e.getAttributeValue(ID_ATTR));
        info.readExternal(e);
        if (info.getOrder() == -1) { // if order isn't defined then window's button will be the last one in the stripe
          info.setOrder(getMaxOrder(info.getAnchor()) + 1);
        }
        myUnregisteredId2Info.put(info.getId(), info);
      }
    }
  }

  public final void writeExternal(final Element layoutElement) {
    final WindowInfoImpl[] infos = getAllInfos();
    for (WindowInfoImpl info : infos) {
      final Element element = new Element(WindowInfoImpl.TAG);
      info.writeExternal(element);
      layoutElement.addContent(element);
    }
  }

  public List<String> getVisibleIdsOn(final ToolWindowAnchor anchor, ToolWindowManagerImpl manager) {
    ArrayList<String> ids = new ArrayList<String>();
    for (WindowInfoImpl each : getInfos()) {
      if (manager == null) break;
      if (each.getAnchor() == anchor) {
        final ToolWindow window = manager.getToolWindow(each.getId());
        if (window == null) continue;
        if (window.isAvailable() || UISettings.getInstance().ALWAYS_SHOW_WINDOW_BUTTONS) {
          ids.add(each.getId());
        }
      }
    }
    return ids;
  }

  private static final class MyWindowInfoComparator implements Comparator<WindowInfoImpl> {
    public int compare(final WindowInfoImpl info1, final WindowInfoImpl info2) {
      return info1.getOrder() - info2.getOrder();
    }
  }

  private final class MyStripeButtonComparator implements Comparator<StripeButton> {
    private final HashMap<String, WindowInfoImpl> myId2Info;

    public MyStripeButtonComparator(final ToolWindowAnchor anchor) {
      myId2Info = new HashMap<String, WindowInfoImpl>();
      final WindowInfoImpl[] infos = getInfos();
      for (final WindowInfoImpl info : infos) {
        if (anchor == info.getAnchor()) {
          myId2Info.put(info.getId(), info.copy());
        }
      }
    }

    public final int compare(final StripeButton obj1, final StripeButton obj2) {
      final WindowInfoImpl info1 = myId2Info.get(obj1.getWindowInfo().getId());
      final int order1 = info1 != null ? info1.getOrder() : 0;

      final WindowInfoImpl info2 = myId2Info.get(obj2.getWindowInfo().getId());
      final int order2 = info2 != null ? info2.getOrder() : 0;

      return order1 - order2;
    }
  }
}
