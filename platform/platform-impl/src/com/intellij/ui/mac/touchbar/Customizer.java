// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class Customizer {
  private final List<ItemCustomizer> myBaseCustomizers = new ArrayList<>();
  private final Map<AnAction, List<ItemCustomizer>> myAct2ButtCustomizer = new HashMap<>();
  private final Map<ActionGroup, List<ItemCustomizer>> myGroupCustomizers = new HashMap<>();
  private final Map<AnAction, ActionGroupInfo> myAct2Parent = new HashMap<>();
  private final Map<AnAction, Object> myAct2Descriptor = new HashMap<>();

  private final @Nullable TBPanel.CrossEscInfo myCrossEscInfo;
  private final String @Nullable[] myAutoCloseActionIds;

  Customizer(@Nullable TBPanel.CrossEscInfo crossEscInfo, String[] autoCloseActionIds, @NotNull ItemCustomizer itemCustomizer) {
    this(crossEscInfo, autoCloseActionIds);
    addBaseCustomizations(itemCustomizer);
  }

  Customizer(@Nullable TBPanel.CrossEscInfo crossEscInfo, String @Nullable[] autoCloseActionIds) {
    myCrossEscInfo = crossEscInfo;
    myAutoCloseActionIds = autoCloseActionIds;
  }

  void prepare(@NotNull ActionGroup actionGroup) {
    myAct2Parent.clear();
    fillAct2Parent(new ActionGroupInfo(actionGroup, null));
  }

  void onBeforeActionsExpand(@NotNull ActionGroup actionGroup) {}

  private void fillAct2Parent(@NotNull ActionGroupInfo actionGroupInfo) {
    @NotNull ActionGroup actionGroup = actionGroupInfo.group;
    AnAction[] actions = actionGroup.getChildren(null);
    for (AnAction childAction : actions) {
      if (childAction == null) {
        continue;
      }
      if (childAction instanceof ActionGroup) {
        fillAct2Parent(new ActionGroupInfo((ActionGroup)childAction, actionGroupInfo));
        continue;
      }
      myAct2Parent.put(childAction, actionGroupInfo);
    }
  }

  @Nullable TBPanel.CrossEscInfo getCrossEscInfo() { return myCrossEscInfo; }

  String @Nullable[] getAutoCloseActionIds() { return myAutoCloseActionIds; }

  boolean applyCustomizations(@NotNull TBItemAnActionButton button, @NotNull Presentation presentation) {
    final @Nullable ActionGroupInfo parentInfo = myAct2Parent.get(button.getAnAction());
    boolean result = false;

    // 1. apply base customizations
    if (!myBaseCustomizers.isEmpty()) {
      result = true;
      for (ItemCustomizer c : myBaseCustomizers) {
        c.applyCustomizations(parentInfo, button, presentation);
      }
    }

    // 2. apply per-action customizations
    final List<ItemCustomizer> customizers = myAct2ButtCustomizer.get(button.getAnAction());
    if (customizers != null && !customizers.isEmpty()) {
      result = true;
      for (ItemCustomizer c : customizers) {
        c.applyCustomizations(parentInfo, button, presentation);
      }
    }

    // 3. apply per-group customizations
    ActionGroupInfo p = parentInfo;
    while (p != null) {
      final List<ItemCustomizer> cs = myGroupCustomizers.get(p.group);
      if (cs != null && !cs.isEmpty()) {
        result = true;
        for (ItemCustomizer c : cs) {
          c.applyCustomizations(parentInfo, button, presentation); // apply with direct parent info (direct parent contains view-descriptors)
        }
      }
      p = p.parent;
    }

    return result;
  }

  boolean isPrincipalGroupAction(@NotNull AnAction action) {
    // 1. check parent group
    ActionGroupInfo groupInfo = myAct2Parent.get(action);
    if (groupInfo != null && groupInfo.hasPrincipalParent()) {
      return true;
    }

    // 2. check action customizations descriptor
    Object descriptor = myAct2Descriptor.get(action);
    return descriptor instanceof TouchbarActionCustomizations && ((TouchbarActionCustomizations)descriptor).isPrincipal();
  }

  //
  // Add customization methods
  //

  void addCustomization(@NotNull AnAction action, @NotNull ItemCustomizer buttCustomizer) {
    List<ItemCustomizer> bcl = myAct2ButtCustomizer.computeIfAbsent(action, (a) -> new ArrayList<>());
    bcl.add(buttCustomizer);
  }

  void addCustomizations(@NotNull AnAction action, ItemCustomizer... customizers) {
    Collections.addAll(myAct2ButtCustomizer.computeIfAbsent(action, (a) -> new ArrayList<>()), customizers);
  }

  void addBaseCustomizations(@NotNull ItemCustomizer customizer) {
    myBaseCustomizers.add(customizer);
  }

  void addGroupCustomization(@NotNull Map<Long, ActionGroup> actionGroups, ItemCustomizer... customizers) {
    for (ActionGroup actionGroup : actionGroups.values()) {
      addGroupCustomization(actionGroup, customizers);
    }
  }

  void addGroupCustomization(@NotNull ActionGroup actionGroup, ItemCustomizer... customizers) {
    Collections.addAll(myGroupCustomizers.computeIfAbsent(actionGroup, (a) -> new ArrayList<>()), customizers);
  }

  void addDescriptor(@NotNull AnAction action, @NotNull Object desc) {
    myAct2Descriptor.put(action, desc);
  }

  //
  // ActionGroupInfo
  //

  static class ActionGroupInfo {
    final @NotNull ActionGroup group;
    final @NotNull String groupID;
    final @Nullable ActionGroupInfo parent;
    final @Nullable TouchbarActionCustomizations groupC;

    ActionGroupInfo(@NotNull ActionGroup group, @Nullable ActionGroupInfo parent) {
      this.group = group;
      this.parent = parent;
      this.groupID = Helpers.getActionId(group);
      this.groupC = TouchbarActionCustomizations.getCustomizations(group);
    }

    @Nullable TouchbarActionCustomizations getCustomizations() { return groupC; }

    boolean hasPrincipalParent() {
      for (ActionGroupInfo p = this; p != null; p = p.parent) {
        if (p.groupC != null && p.groupC.isPrincipal()) {
          return true;
        }
      }
      return false;
    }
  }

  interface ItemCustomizer {
    void applyCustomizations(@Nullable ActionGroupInfo parentGroupInfo, @NotNull TBItemAnActionButton butt, @NotNull Presentation presentation);
  }
}
