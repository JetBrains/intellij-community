// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.intellij.configurationStore.XmlSerializer.serialize;

/**
 * @author Vladimir Kondratyev
 */
public final class DesktopLayout {
  private static final Logger LOG = Logger.getInstance(DesktopLayout.class);

  private static int getAnchorWeight(@NotNull ToolWindowAnchor anchor) {
    if (anchor == ToolWindowAnchor.TOP) {
      return SwingConstants.TOP;
    }
    if (anchor == ToolWindowAnchor.LEFT) {
      return SwingConstants.LEFT;
    }
    if (anchor == ToolWindowAnchor.BOTTOM) {
      return SwingConstants.BOTTOM;
    }
    if (anchor == ToolWindowAnchor.RIGHT) {
      return SwingConstants.RIGHT;
    }
    return 0;
  }

  private static final Comparator<WindowInfoImpl> ourWindowInfoComparator = (o1, o2) -> {
    int d = getAnchorWeight(o1.getAnchor()) - getAnchorWeight(o2.getAnchor());
    return d == 0 ? o1.getOrder() - o2.getOrder() : d;
  };

  static final String TAG = "layout";

  /**
   * Map between {@code id}s {@code WindowInfo}s.
   */
  private final Map<String, WindowInfoImpl> myIdToInfo = new THashMap<>();

  private final ClearableLazyValue<List<WindowInfoImpl>> myRegisteredInfos = new ClearableLazyValue<List<WindowInfoImpl>>() {
    @NotNull
    @Override
    protected List<WindowInfoImpl> compute() {
      if (myIdToInfo.isEmpty()) {
        return Collections.emptyList();
      }

      List<WindowInfoImpl> result = new ArrayList<>();
      for (WindowInfoImpl value : myIdToInfo.values()) {
        if (value.isRegistered()) {
          result.add(value);
        }
      }
      result.sort(ourWindowInfoComparator);
      return result;
    }
  };

  /**
   * Copies itself from the passed
   * @param layout to be copied.
   */
  public final void copyFrom(@NotNull DesktopLayout layout) {
    Map<String, WindowInfoImpl> old = new THashMap<>(myIdToInfo);
    myIdToInfo.clear();
    for (WindowInfoImpl otherInfo : layout.myIdToInfo.values()) {
      WindowInfoImpl oldInfo = old.get(otherInfo.getId());
      if (oldInfo == null) {
        WindowInfoImpl newInfo = otherInfo.copy();
        newInfo.setRegistered(otherInfo.isRegistered());
        myIdToInfo.put(otherInfo.getId(), newInfo);
      }
      else {
        oldInfo.copyFrom(otherInfo);
        oldInfo.setRegistered(otherInfo.isRegistered());
        myIdToInfo.put(otherInfo.getId(), oldInfo);
      }
    }

    normalizeOrders();
  }

  private void normalizeOrders() {
    normalizeOrder(getAllInfos(ToolWindowAnchor.TOP));
    normalizeOrder(getAllInfos(ToolWindowAnchor.LEFT));
    normalizeOrder(getAllInfos(ToolWindowAnchor.BOTTOM));
    normalizeOrder(getAllInfos(ToolWindowAnchor.RIGHT));

    myRegisteredInfos.drop();
  }

  /**
   * Creates or gets {@code WindowInfo} for the specified {@code id}. If tool
   * window is being registered first time the method uses {@code anchor}.
   *
   * @param id     {@code id} of tool window to be registered.
   * @param anchor the default tool window anchor.
   */
  final WindowInfoImpl register(@NotNull String id, @NotNull ToolWindowAnchor anchor, final boolean splitMode) {
    WindowInfoImpl info = myIdToInfo.get(id);
    if (info == null) {
      info = new WindowInfoImpl();
      info.setId(id);
      info.setAnchor(anchor);
      info.setSplit(splitMode);
      myIdToInfo.put(id, info);
    }
    if (!info.isRegistered()) {
      info.setRegistered(true);
      myRegisteredInfos.drop();
    }
    return info;
  }

  final void unregister(@NotNull String id) {
    WindowInfoImpl info = myIdToInfo.get(id);
    if (info.isRegistered()) {
      info.setRegistered(false);
      myRegisteredInfos.drop();
    }
  }

  /**
   * @return {@code WindowInfo} for the window with specified {@code id}.
   *         If {@code onlyRegistered} is {@code true} then returns not {@code null}
   *         value if and only if window with {@code id} is registered one.
   */
  final WindowInfoImpl getInfo(@NotNull String id, final boolean onlyRegistered) {
    WindowInfoImpl info = myIdToInfo.get(id);
    if (onlyRegistered && info != null && !info.isRegistered()) {
      return null;
    }
    return info;
  }

  @Nullable
  final String getActiveId() {
    for (WindowInfoImpl info : getInfos()) {
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
  final List<WindowInfoImpl> getInfos() {
    return myRegisteredInfos.getValue();
  }

  /**
   * @return all (registered and not unregistered) {@code WindowInfos} for the specified {@code anchor}.
   *         Returned infos are sorted by order.
   */
  @NotNull
  private List<WindowInfoImpl> getAllInfos(@NotNull ToolWindowAnchor anchor) {
    List<WindowInfoImpl> result = new ArrayList<>();
    for (WindowInfoImpl info : myIdToInfo.values()) {
      if (anchor == info.getAnchor()) {
        result.add(info);
      }
    }
    result.sort(ourWindowInfoComparator);
    return result;
  }

  /**
   * Normalizes order of windows in the passed array. Note, that array should be
   * sorted by order (by ascending). Order of first window will be {@code 0}.
   */
  private static void normalizeOrder(@NotNull List<WindowInfoImpl> infos) {
    for (int i = 0; i < infos.size(); i++) {
      infos.get(i).setOrder(i);
    }
  }

  final boolean isToolWindowRegistered(@NotNull String id) {
    WindowInfoImpl info = myIdToInfo.get(id);
    return info != null && info.isRegistered();
  }

  /**
   * @return comparator which compares {@code StripeButtons} in the stripe with specified {@code anchor}.
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
    for (WindowInfoImpl info : myIdToInfo.values()) {
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
    if (newOrder == -1) {
      // if order isn't defined then the window will the last in the stripe
      newOrder = getMaxOrder(newAnchor) + 1;
    }
    final WindowInfoImpl info = getInfo(id, true);
    final ToolWindowAnchor oldAnchor = info.getAnchor();
    // shift order to the right in the target stripe
    final List<WindowInfoImpl> infos = getAllInfos(newAnchor);
    for (int i = infos.size() - 1; i > -1; i--) {
      final WindowInfoImpl info2 = infos.get(i);
      if (newOrder <= info2.getOrder()) {
        info2.setOrder(info2.getOrder() + 1);
      }
    }
    // "move" window into the target position
    info.setAnchor(newAnchor);
    info.setOrder(newOrder);
    // normalize orders in the source and target stripes
    normalizeOrder(getAllInfos(oldAnchor));
    if (oldAnchor != newAnchor) {
      normalizeOrder(getAllInfos(newAnchor));
    }

    myRegisteredInfos.drop();
  }

  final void setSplitMode(@NotNull String id, boolean split) {
    getInfo(id, true).setSplit(split);
  }

  public final void readExternal(@NotNull Element layoutElement) {
    Set<String> registered = new THashSet<>();
    for (WindowInfoImpl info : myIdToInfo.values()) {
      if (info.isRegistered()) {
        registered.add(info.getId());
      }
    }

    myIdToInfo.clear();
    for (Element e : layoutElement.getChildren(WindowInfoImpl.TAG)) {
      WindowInfoImpl info = XmlSerializer.deserialize(e, WindowInfoImpl.class);
      info.normalizeAfterRead();
      if (info.getId() == null) {
        LOG.warn("Skip invalid window info (no id): " + JDOMUtil.writeElement(e));
        continue;
      }

      if (registered.contains(info.getId())) {
        info.setRegistered(true);
      }

      myIdToInfo.put(info.getId(), info);
    }

    for (WindowInfoImpl info : myIdToInfo.values()) {
      if (info.getOrder() == -1) {
        // if order isn't defined then window's button will be the last one in the stripe
        info.setOrder(getMaxOrder(info.getAnchor()) + 1);
      }
    }

    normalizeOrders();
  }

  @Nullable
  public final Element writeExternal(@NotNull String tagName) {
    if (myIdToInfo.isEmpty()) {
      return null;
    }

    List<WindowInfoImpl> list = new ArrayList<>(myIdToInfo.values());
    list.sort(ourWindowInfoComparator);
    Element state = new Element(tagName);
    for (WindowInfoImpl info : list) {
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

  private final class MyStripeButtonComparator implements Comparator<StripeButton> {
    private final Map<String, WindowInfoImpl> myIdToInfo = new THashMap<>();

    public MyStripeButtonComparator(@NotNull ToolWindowAnchor anchor) {
      for (WindowInfoImpl info : getInfos()) {
        if (anchor == info.getAnchor()) {
          myIdToInfo.put(info.getId(), info.copy());
        }
      }
    }

    @Override
    public final int compare(final StripeButton obj1, final StripeButton obj2) {
      final WindowInfoImpl info1 = myIdToInfo.get(obj1.getWindowInfo().getId());
      final int order1 = info1 != null ? info1.getOrder() : 0;

      final WindowInfoImpl info2 = myIdToInfo.get(obj2.getWindowInfo().getId());
      final int order2 = info2 != null ? info2.getOrder() : 0;

      return order1 - order2;
    }
  }
}
