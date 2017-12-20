/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.wm.impl;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.configurationStore.XmlSerializer.serialize;

/**
 * @author Vladimir Kondratyev
 */
public final class DesktopLayout {
  private static final Logger LOG = Logger.getInstance(DesktopLayout.class);

  @NonNls static final String TAG = "layout";
  /**
   * Map between {@code id}s and registered {@code WindowInfo}s.
   */
  private final Map<String, WindowInfoImpl> myRegisteredId2Info = new HashMap<>();
  /**
   * Map between {@code id}s and unregistered {@code WindowInfo}s.
   */
  private final Map<String, WindowInfoImpl> myUnregisteredId2Info = new THashMap<>();
  /**
   *
   */
  private static final MyWindowInfoComparator ourWindowInfoComparator = new MyWindowInfoComparator();
  /**
   * Don't use this member directly. Get it only by {@code getInfos} method.
   * It exists here only for optimization purposes. This member can be {@code null}
   * if the cached data is invalid.
   */
  private WindowInfoImpl[] myRegisteredInfos;
  /**
   * Don't use this member directly. Get it only by {@code getUnregisteredInfos} method.
   * It exists here only for optimization purposes. This member can be {@code null}
   * if the cached data is invalid.
   */
  private WindowInfoImpl[] myUnregisteredInfos;
  /**
   * Don't use this member directly. Get it only by {@code getAllInfos} method.
   * It exists here only for optimization purposes. This member can be {@code null}
   * if the cached data is invalid.
   */
  private WindowInfoImpl[] myAllInfos;

  /**
   * Copies itself from the passed
   *
   * @param layout to be copied.
   */
  public final void copyFrom(@NotNull DesktopLayout layout) {
    for (WindowInfoImpl info1 : layout.getAllInfos()) {
      WindowInfoImpl info = myRegisteredId2Info.get(info1.getId());
      if (info != null) {
        info.copyFrom(info1);
        continue;
      }
      info = myUnregisteredId2Info.get(info1.getId());
      if (info == null) {
        myUnregisteredId2Info.put(info1.getId(), info1.copy());
      }
      else {
        info.copyFrom(info1);
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
   * Creates or gets {@code WindowInfo} for the specified {@code id}. If tool
   * window is being registered first time the method uses {@code anchor}.
   *
   * @param id     {@code id} of tool window to be registered.
   * @param anchor the default tool window anchor.
   */
  final WindowInfoImpl register(@NotNull String id, @NotNull ToolWindowAnchor anchor, final boolean splitMode) {
    WindowInfoImpl info = myUnregisteredId2Info.get(id);
    if (info == null) {
      // tool window is being registered first time
      info = new WindowInfoImpl();
      info.setId(id);
      info.setAnchor(anchor);
      info.setSplit(splitMode);
    }
    else {
      // tool window has been already registered some time
      myUnregisteredId2Info.remove(id);
    }
    myRegisteredId2Info.put(id, info);
    // invalidate caches
    myRegisteredInfos = null;
    myUnregisteredInfos = null;
    myAllInfos = null;
    return info;
  }

  final void unregister(@NotNull String id) {
    final WindowInfoImpl info = myRegisteredId2Info.remove(id).copy();
    myUnregisteredId2Info.put(id, info);
    // invalidate caches
    myRegisteredInfos = null;
    myUnregisteredInfos = null;
    myAllInfos = null;
  }

  /**
   * @return {@code WindowInfo} for the window with specified {@code id}.
   *         If {@code onlyRegistered} is {@code true} then returns not {@code null}
   *         value if and only if window with {@code id} is registered one.
   */
  final WindowInfoImpl getInfo(@NotNull String id, final boolean onlyRegistered) {
    final WindowInfoImpl info = myRegisteredId2Info.get(id);
    if (onlyRegistered || info != null) {
      return info;
    }
    return myUnregisteredId2Info.get(id);
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
   * @return {@code WindowInfo}s for all registered tool windows.
   */
  @NotNull
  final WindowInfoImpl[] getInfos() {
    if (myRegisteredInfos == null) {
      myRegisteredInfos = myRegisteredId2Info.values().toArray(new WindowInfoImpl[myRegisteredId2Info.size()]);
    }
    return myRegisteredInfos;
  }

  /**
   * @return {@code WindowInfos}s for all windows that are currently unregistered.
   */
  @NotNull
  private WindowInfoImpl[] getUnregisteredInfos() {
    if (myUnregisteredInfos == null) {
      myUnregisteredInfos = myUnregisteredId2Info.values().toArray(new WindowInfoImpl[myUnregisteredId2Info.size()]);
    }
    return myUnregisteredInfos;
  }

  /**
   * @return {@code WindowInfo}s of all (registered and unregistered) tool windows.
   */
  @NotNull
  private WindowInfoImpl[] getAllInfos() {
    final WindowInfoImpl[] registeredInfos = getInfos();
    final WindowInfoImpl[] unregisteredInfos = getUnregisteredInfos();
    myAllInfos = ArrayUtil.mergeArrays(registeredInfos, unregisteredInfos);
    return myAllInfos;
  }

  /**
   * @return all (registered and not unregistered) {@code WindowInfos} for the specified {@code anchor}.
   *         Returned infos are sorted by order.
   */
  @NotNull
  private WindowInfoImpl[] getAllInfos(@NotNull ToolWindowAnchor anchor) {
    WindowInfoImpl[] infos = getAllInfos();
    final ArrayList<WindowInfoImpl> list = new ArrayList<>(infos.length);
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
   * sorted by order (by ascending). Order of first window will be {@code 0}.
   */
  private static void normalizeOrder(@NotNull WindowInfoImpl[] infos) {
    for (int i = 0; i < infos.length; i++) {
      infos[i].setOrder(i);
    }
  }

  final boolean isToolWindowRegistered(@NotNull String id) {
    return myRegisteredId2Info.containsKey(id);
  }

  final boolean isToolWindowUnregistered(@NotNull String id) {
    return myUnregisteredId2Info.containsKey(id);
  }

  /**
   * @return comparator which compares {@code StripeButtons} in the stripe with
   *         specified {@code anchor}.
   */
  @NotNull
  final Comparator<StripeButton> comparator(@NotNull ToolWindowAnchor anchor) {
    return new MyStripeButtonComparator(anchor);
  }

  /**
   * @param anchor anchor of the stripe.
   * @return maximum ordinal number in the specified stripe. Returns {@code -1}
   *         if there is no any tool window with the specified anchor.
   */
  private int getMaxOrder(@NotNull ToolWindowAnchor anchor) {
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
   * Sets new {@code anchor} and {@code id} for the specified tool window.
   * Also the method properly updates order of all other tool windows.
   *
   * @param newAnchor new anchor
   * @param newOrder  new order
   */
  final void setAnchor(@NotNull String id, @NotNull ToolWindowAnchor newAnchor, int newOrder) {
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

  final void setSplitMode(@NotNull String id, boolean split) {
    final WindowInfoImpl info = getInfo(id, true);
    info.setSplit(split);
  }

  public final void readExternal(@NotNull Element layoutElement) {
    myUnregisteredInfos = null;
    for (Element e : layoutElement.getChildren(WindowInfoImpl.TAG)) {
      WindowInfoImpl info = XmlSerializer.deserialize(e, WindowInfoImpl.class);
      if (info.getId() == null) {
        LOG.warn("Skip invalid window info (no id): " + JDOMUtil.writeElement(e));
        continue;
      }

      if (info.getOrder() == -1) {
        // if order isn't defined then window's button will be the last one in the stripe
        info.setOrder(getMaxOrder(info.getAnchor()) + 1);
      }
      myUnregisteredId2Info.put(info.getId(), info);
    }
  }

  @Nullable
  public final Element writeExternal(@NotNull String tagName) {
    WindowInfoImpl[] infos = getAllInfos();
    if (infos.length == 0) {
      return null;
    }

    Element state = new Element(tagName);
    for (WindowInfoImpl info : infos) {
      Element element = serialize(info);
      if (element != null) {
        state.addContent(element);
      }
    }
    return state;
  }

  @NotNull
  List<String> getVisibleIdsOn(@NotNull ToolWindowAnchor anchor, @NotNull ToolWindowManagerImpl manager) {
    List<String> ids = new ArrayList<>();
    for (WindowInfoImpl each : getAllInfos(anchor)) {
      final ToolWindow window = manager.getToolWindow(each.getId());
      if (window == null) continue;
      if (window.isAvailable() || UISettings.getInstance().getAlwaysShowWindowsButton()) {
        ids.add(each.getId());
      }
    }
    return ids;
  }

  private static final class MyWindowInfoComparator implements Comparator<WindowInfoImpl> {
    @Override
    public int compare(final WindowInfoImpl info1, final WindowInfoImpl info2) {
      return info1.getOrder() - info2.getOrder();
    }
  }

  private final class MyStripeButtonComparator implements Comparator<StripeButton> {
    private final HashMap<String, WindowInfoImpl> myId2Info = new HashMap<>();

    public MyStripeButtonComparator(@NotNull ToolWindowAnchor anchor) {
      final WindowInfoImpl[] infos = getInfos();
      for (final WindowInfoImpl info : infos) {
        if (anchor == info.getAnchor()) {
          myId2Info.put(info.getId(), info.copy());
        }
      }
    }

    @Override
    public final int compare(final StripeButton obj1, final StripeButton obj2) {
      final WindowInfoImpl info1 = myId2Info.get(obj1.getWindowInfo().getId());
      final int order1 = info1 != null ? info1.getOrder() : 0;

      final WindowInfoImpl info2 = myId2Info.get(obj2.getWindowInfo().getId());
      final int order2 = info2 != null ? info2.getOrder() : 0;

      return order1 - order2;
    }
  }
}
