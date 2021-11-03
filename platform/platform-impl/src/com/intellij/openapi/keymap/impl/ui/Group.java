// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Group implements KeymapGroup {
  private Group myParent;
  private final @NlsActions.ActionText String myName;
  private final String myId;
  private final Icon myIcon;
  /**
   * Group or action id (String) or Separator or QuickList or Hyperlink
   */
  private final ArrayList<Object> myChildren;

  private final Set<String> myIds = new HashSet<>();

  public Group(@NlsActions.ActionText String name, String id, Icon icon) {
    myName = name;
    myId = id;
    myIcon = icon;
    myChildren = new ArrayList<>();
  }

  public Group(final @NlsActions.ActionText String name, final Icon icon) {
    this(name, null, icon);
  }

  public @NlsActions.ActionText String getName() {
    return myName;
  }

  public Icon getIcon() {
    return myIcon;
  }

  @Nullable
  public String getId() {
    return myId;
  }

  @Override
  public void addActionId(String id) {
    if (myChildren.contains(id)) return;
    myChildren.add(id);
  }

  public void addQuickList(QuickList list) {
    myChildren.add(list);
  }

  public void addHyperlink(Hyperlink link) {
    myChildren.add(link);
  }

  @Override
  public void addGroup(KeymapGroup keymapGroup) {
    Group group = (Group) keymapGroup;
    if (myChildren.contains(group)) return;
    myChildren.add(group);
    group.myParent = this;
  }

  public void addSeparator() {
    myChildren.add(Separator.getInstance());
  }

  public boolean containsId(String id) {
    return myIds.contains(id);
  }

  public Set<String> initIds(){
    for (Object child : myChildren) {
      if (child instanceof String) {
        myIds.add((String)child);
      }
      else if (child instanceof QuickList) {
        myIds.add(((QuickList)child).getActionId());
      }
      else if (child instanceof Group) {
        Group childGroup = (Group)child;
        myIds.addAll(childGroup.initIds());
        if (childGroup.myId != null) {
          myIds.add(childGroup.myId);
        }
      }
    }
    return myIds;
  }

  public ArrayList<Object> getChildren() {
    return myChildren;
  }

  public int getSize() {
    return myChildren.size();
  }

  public void normalizeSeparators() {
    while (myChildren.size() > 0 && myChildren.get(0) instanceof Separator) {
      myChildren.remove(0);
    }

    while (myChildren.size() > 0 && myChildren.get(myChildren.size() - 1) instanceof Separator) {
      myChildren.remove(myChildren.size() - 1);
    }

    for (int i=1; i < myChildren.size() - 1; i++) {
      if (myChildren.get(i) instanceof Separator && myChildren.get(i + 1) instanceof Separator) {
        myChildren.remove(i);
        i--;
      }
    }
  }

  public String getActionQualifiedPath(String id, boolean presentable) {
    Group cur = myParent;
    StringBuilder answer = new StringBuilder();

    while (cur != null && !cur.isRoot()) {
      answer.insert(0, cur.getName(presentable) + " | ");

      cur = cur.myParent;
    }

    String suffix = calcActionQualifiedPath(id, presentable);
    if (StringUtil.isEmpty(suffix)) return null;

    answer.append(suffix);

    return answer.toString();
  }

  private String calcActionQualifiedPath(String id, boolean presentable) {
    if (!isRoot() && StringUtil.equals(id, myId)) {
      return getName(presentable);
    }
    for (Object child : myChildren) {
      if (child instanceof QuickList) {
        child = ((QuickList)child).getActionId();
      }
      if (child instanceof String) {
        if (id.equals(child)) {
          AnAction action = presentable ? ActionManager.getInstance().getActionOrStub(id) : null;
          String path;
          if (action != null) {
            path = action.getTemplatePresentation().getText();
          }
          else {
            path = id;
          }
          return !isRoot() ? getName(presentable) + " | " + path : path;
        }
      }
      else if (child instanceof Group) {
        String path = ((Group)child).calcActionQualifiedPath(id, presentable);
        if (path != null) {
          return !isRoot() ? getName(presentable) + " | " + path : path;
        }
      }
    }
    return null;
  }

  @Nullable
  private String getName(boolean presentable) {
    return presentable ? getName() : getId();
  }

  public boolean isRoot() {
    return myParent == null;
  }

  public String getQualifiedPath(boolean presentable) {
    StringBuilder path = new StringBuilder(64);
    Group group = this;
    while (group != null && !group.isRoot()) {
      if (path.length() > 0) path.insert(0, " | ");
      path.insert(0, group.getName(presentable));
      group = group.myParent;
    }
    return path.toString();
  }

  public void addAll(Group group) {
    for (Object o : group.getChildren()) {
      if (o instanceof String) {
        addActionId((String)o);
      }
      else if (o instanceof QuickList) {
        addQuickList((QuickList)o);
      }
      else if (o instanceof Group) {
        addGroup((Group)o);
      }
      else if (o instanceof Separator) {
        addSeparator();
      }
    }
    if (group.myId != null) {
      myIds.add(group.myId);
    }
  }

  public ActionGroup constructActionGroup(final boolean popup){
    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup group = new DefaultActionGroup(getName(), popup);
    AnAction groupToRestorePresentation = null;
    if (getName() != null){
      groupToRestorePresentation = actionManager.getAction(getName());
    } else {
      if (getId() != null){
        groupToRestorePresentation = actionManager.getAction(getId());
      }
    }
    if (groupToRestorePresentation != null){
      group.copyFrom(groupToRestorePresentation);
    }
    for (Object o : myChildren) {
      if (o instanceof String) {
        group.add(actionManager.getAction((String)o));
      }
      else if (o instanceof Separator) {
        group.addSeparator();
      }
      else if (o instanceof Group) {
        group.add(((Group)o).constructActionGroup(popup));
      }
    }
    return group;
  }


  @Override
  public boolean equals(Object object) {
    if (!(object instanceof Group)) return false;
    final Group group = ((Group)object);
    if (group.getName() != null && getName() != null){
      return group.getName().equals(getName());
    }
    if (getChildren() != null && group.getChildren() != null){
      if (getChildren().size() != group.getChildren().size()){
        return false;
      }

      for (int i = 0; i < getChildren().size(); i++) {
        if (!getChildren().get(i).equals(group.getChildren().get(i))){
          return false;
        }
      }
      return true;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return getName() != null ? getName().hashCode() : 0;
  }

  @Override
  public String toString() {
    return getName();
  }
}
