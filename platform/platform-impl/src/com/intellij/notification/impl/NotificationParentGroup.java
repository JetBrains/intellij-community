/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.notification.impl;

import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Alexander Lobas
 */
public class NotificationParentGroup {
  private static final ExtensionPointName<NotificationParentGroupBean> EP_NAME =
    ExtensionPointName.create("com.intellij.notification.parentGroup");
  private static final ExtensionPointName<NotificationGroupBean> EP_CHILD_NAME =
    ExtensionPointName.create("com.intellij.notification.group");

  private static Map<String, NotificationParentGroupBean> myParents;
  private static Map<NotificationParentGroupBean, List<NotificationParentGroupBean>> myChildren;
  private static Map<String, NotificationParentGroupBean> myGroupToParent;
  private static Map<String, String> myReplaceTitles;
  private static Map<String, String> myShortTitles;

  private static void prepareInfo() {
    if (myParents == null) {
      myParents = new HashMap<>();
      NotificationParentGroupBean[] parents = EP_NAME.getExtensions();
      for (NotificationParentGroupBean bean : parents) {
        myParents.put(bean.id, bean);
      }

      myChildren = new HashMap<>();
      for (NotificationParentGroupBean bean : parents) {
        if (bean.parentId != null) {
          NotificationParentGroupBean parent = myParents.get(bean.parentId);
          if (parent != null) {
            List<NotificationParentGroupBean> children = myChildren.get(parent);
            if (children == null) {
              myChildren.put(parent, children = new ArrayList<>());
            }
            children.add(bean);
          }
        }
      }

      myGroupToParent = new HashMap<>();
      myReplaceTitles = new HashMap<>();
      myShortTitles = new HashMap<>();
      for (NotificationGroupBean bean : EP_CHILD_NAME.getExtensions()) {
        NotificationParentGroupBean parent = myParents.get(bean.parentId);
        if (parent != null) {
          myGroupToParent.put(bean.groupId, parent);

          if (bean.replaceTitle != null) {
            myReplaceTitles.put(bean.groupId, bean.replaceTitle);
          }
          if (bean.shortTitle != null) {
            myShortTitles.put(bean.groupId, bean.shortTitle);
          }
        }
      }
    }
  }

  @Nullable
  public static String getReplaceTitle(@NotNull String groupId) {
    prepareInfo();
    return myReplaceTitles.get(groupId);
  }

  @Nullable
  public static String getShortTitle(@NotNull String groupId) {
    prepareInfo();
    return myShortTitles.get(groupId);
  }

  @NotNull
  public static List<NotificationParentGroupBean> getChildren(@NotNull NotificationParentGroupBean parent) {
    prepareInfo();
    List<NotificationParentGroupBean> children = myChildren.get(parent);
    return children == null ? Collections.emptyList() : children;
  }

  @NotNull
  public static Collection<NotificationParentGroupBean> getParents() {
    prepareInfo();
    return Collections.unmodifiableCollection(myParents.values());
  }

  @Nullable
  public static NotificationParentGroupBean findParent(@NotNull NotificationSettings setting) {
    prepareInfo();

    String groupId = setting.getGroupId();
    NotificationGroup group = NotificationGroup.findRegisteredGroup(groupId);
    NotificationParentGroupBean parent;

    if (group == null) {
      parent = myGroupToParent.get(groupId);
    }
    else {
      String parentId = group.getParentId();
      if (parentId == null) {
        parent = myGroupToParent.get(group.getDisplayId());
        if (parent != null) {
          group.setParentId(parent.id);
        }
      }
      else {
        parent = myParents.get(parentId);
      }
    }

    return parent;
  }
}