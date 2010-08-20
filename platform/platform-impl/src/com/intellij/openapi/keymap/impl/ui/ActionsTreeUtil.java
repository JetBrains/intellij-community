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
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.ide.actionMacro.ActionMacro;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapExtension;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.*;

public class ActionsTreeUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil");

  public static final String MAIN_MENU_TITLE = KeyMapBundle.message("main.menu.action.title");
  public static final String MAIN_TOOLBAR = KeyMapBundle.message("main.toolbar.title");
  public static final String EDITOR_POPUP = KeyMapBundle.message("editor.popup.menu.title");

  private static final Icon MAIN_MENU_ICON = IconLoader.getIcon("/nodes/keymapMainMenu.png");
  private static final Icon EDITOR_ICON = IconLoader.getIcon("/nodes/keymapEditor.png");
  private static final Icon EDITOR_OPEN_ICON = IconLoader.getIcon("/nodes/keymapEditorOpen.png");
  private static final Icon OTHER_ICON = IconLoader.getIcon("/nodes/keymapOther.png");
  public static final String EDITOR_TAB_POPUP = KeyMapBundle.message("editor.tab.popup.menu.title");
  public static final String FAVORITES_POPUP = KeyMapBundle.message("favorites.popup.title");
  public static final String PROJECT_VIEW_POPUP = KeyMapBundle.message("project.view.popup.menu.title");
  public static final String COMMANDER_POPUP = KeyMapBundle.message("commender.view.popup.menu.title");
  public static final String J2EE_POPUP = KeyMapBundle.message("j2ee.view.popup.menu.title");

  @NonNls
  private static final String EDITOR_PREFIX = "Editor";
  @NonNls private static final String TOOL_ACTION_PREFIX = "Tool_";

  private ActionsTreeUtil() {
  }

  private static Group createPluginsActionsGroup(Condition<AnAction> filtered, Group otherGroup) {
    Group pluginsGroup = new Group(KeyMapBundle.message("plugins.group.title"), null, null);
    final KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
    ActionManagerEx managerEx = ActionManagerEx.getInstanceEx();
    final List<IdeaPluginDescriptor> plugins = new ArrayList<IdeaPluginDescriptor>();
    Collections.addAll(plugins, ApplicationManager.getApplication().getPlugins());
    Collections.sort(plugins, new Comparator<IdeaPluginDescriptor>() {
      public int compare(IdeaPluginDescriptor o1, IdeaPluginDescriptor o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    for (IdeaPluginDescriptor plugin : plugins) {
      Group pluginGroup;
      if (plugin.getName().equals("IDEA CORE")) {
        continue;
      }
      else {
        pluginGroup = new Group(plugin.getName(), null, null);
      }
      final String[] pluginActions = managerEx.getPluginActions(plugin.getPluginId());
      if (pluginActions == null || pluginActions.length == 0) {
        continue;
      }
      Arrays.sort(pluginActions, new Comparator<String>() {
        public int compare(String o1, String o2) {
          return getTextToCompare(o1).compareTo(getTextToCompare(o2));
        }
      });
      for (String pluginAction : pluginActions) {
        if (keymapManager.getBoundActions().contains(pluginAction)) continue;
        final AnAction anAction = managerEx.getActionOrStub(pluginAction);
        if (filtered == null || filtered.value(anAction)) {
          pluginGroup.addActionId(pluginAction);
        }
      }
      if (pluginGroup.getSize() > 0 && pluginGroup != otherGroup) {
        pluginsGroup.addGroup(pluginGroup);
      }
    }
    return pluginsGroup;
  }

  private static Group createMainMenuGroup(Condition<AnAction> filtered) {
    Group group = new Group(MAIN_MENU_TITLE, IdeActions.GROUP_MAIN_MENU, MAIN_MENU_ICON, MAIN_MENU_ICON);
    ActionGroup mainMenuGroup = (ActionGroup)ActionManager.getInstance().getActionOrStub(IdeActions.GROUP_MAIN_MENU);
    fillGroupIgnorePopupFlag(mainMenuGroup, group, filtered);
    return group;
  }

  private static void fillGroupIgnorePopupFlag(ActionGroup actionGroup, Group group, Condition<AnAction> filtered) {
    AnAction[] mainMenuTopGroups = actionGroup instanceof DefaultActionGroup
                                   ? ((DefaultActionGroup)actionGroup).getChildActionsOrStubs()
                                   : actionGroup.getChildren(null);
    for (AnAction action : mainMenuTopGroups) {
      Group subGroup = createGroup((ActionGroup)action, false, filtered);
      if (subGroup.getSize() > 0) {
        group.addGroup(subGroup);
      }
    }
  }

  public static Group createGroup(ActionGroup actionGroup, boolean ignore, Condition<AnAction> filtered) {

    return createGroup(actionGroup, getName(actionGroup), null, null, ignore, filtered);
  }

  private static String getName(AnAction action) {
    final String name = action.getTemplatePresentation().getText();
    if (name != null && !name.isEmpty()) {
      return name;
    }
    else {
      final String id = ActionManager.getInstance().getId(action);
      if (id != null) {
        return id;
      }
      if (action instanceof DefaultActionGroup) {
        final DefaultActionGroup group = (DefaultActionGroup)action;
        if (group.getChildrenCount() == 0) return "Empty group";
        final AnAction[] children = group.getChildActionsOrStubs();
        for (AnAction child : children) {
          if (!(child instanceof Separator)) {
            return "group." + getName(child);
          }
        }
        return "Empty unnamed group";
      }
      return action.getClass().getName();
    }
  }

  public static Group createGroup(ActionGroup actionGroup,
                                  String groupName,
                                  Icon icon,
                                  Icon openIcon,
                                  boolean ignore,
                                  Condition<AnAction> filtered) {
    return createGroup(actionGroup, groupName, icon, openIcon, ignore, filtered, true);
  }

  public static Group createGroup(ActionGroup actionGroup, String groupName, Icon icon, Icon openIcon, boolean ignore, Condition<AnAction> filtered,
                                  boolean normalizeSeparators) {
    ActionManager actionManager = ActionManager.getInstance();
    Group group = new Group(groupName, actionManager.getId(actionGroup), icon, openIcon);
    AnAction[] children = actionGroup instanceof DefaultActionGroup
                          ? ((DefaultActionGroup)actionGroup).getChildActionsOrStubs()
                          : actionGroup.getChildren(null);

    for (int i = 0; i < children.length; i++) {
      AnAction action = children[i];
      LOG.assertTrue(action != null, groupName + " contains null actions");
      if (action instanceof ActionGroup) {
        Group subGroup = createGroup((ActionGroup)action, getName(action), null, null, ignore, filtered, normalizeSeparators);
        if (subGroup.getSize() > 0) {
          if (!ignore && !((ActionGroup)action).isPopup()) {
            group.addAll(subGroup);
          }
          else {
            group.addGroup(subGroup);
          }
        }
        else if (filtered == null || filtered.value(actionGroup)) {
          group.addGroup(subGroup);
        }
      }
      else if (action instanceof Separator) {
        group.addSeparator();
      }
      else if (action != null) {
        String id = action instanceof ActionStub ? ((ActionStub)action).getId() : actionManager.getId(action);
        if (id != null) {
          if (id.startsWith(TOOL_ACTION_PREFIX)) continue;
          if (filtered == null || filtered.value(action)) {
            group.addActionId(id);
          }
        }
      }
    }
    if (normalizeSeparators) group.normalizeSeparators();
    return group;
  }

  private static Group createEditorActionsGroup(Condition<AnAction> filtered) {
    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup editorGroup = (DefaultActionGroup)actionManager.getActionOrStub(IdeActions.GROUP_EDITOR);
    ArrayList<String> ids = new ArrayList<String>();

    addEditorActions(filtered, editorGroup, ids);

    Collections.sort(ids);
    Group group = new Group(KeyMapBundle.message("editor.actions.group.title"), IdeActions.GROUP_EDITOR, EDITOR_ICON, EDITOR_OPEN_ICON);
    for (String id : ids) {
      group.addActionId(id);
    }

    return group;
  }

  private static void addEditorActions(final Condition<AnAction> filtered,
                                       final DefaultActionGroup editorGroup,
                                       final ArrayList<String> ids) {
    AnAction[] editorActions = editorGroup.getChildActionsOrStubs();
    final ActionManager actionManager = ActionManager.getInstance();
    for (AnAction editorAction : editorActions) {
      if (editorAction instanceof DefaultActionGroup) {
        addEditorActions(filtered, (DefaultActionGroup) editorAction, ids);
      }
      else {
        String actionId = editorAction instanceof ActionStub ? ((ActionStub)editorAction).getId() : actionManager.getId(editorAction);
        if (actionId == null) continue;
        if (actionId.startsWith(EDITOR_PREFIX)) {
          AnAction action = actionManager.getActionOrStub('$' + actionId.substring(6));
          if (action != null) continue;
        }
        if (filtered == null || filtered.value(editorAction)) {
          ids.add(actionId);
        }
      }
    }
  }

  private static Group createExtensionGroup(Condition<AnAction> filtered, final Project project, KeymapExtension provider) {
    return (Group) provider.createGroup(filtered, project);
  }

  private static Group createMacrosGroup(Condition<AnAction> filtered) {
    final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    String[] ids = actionManager.getActionIds(ActionMacro.MACRO_ACTION_PREFIX);
    Arrays.sort(ids);
    Group group = new Group(KeyMapBundle.message("macros.group.title"), null, null);
    for (String id : ids) {
      if (filtered == null || filtered.value(actionManager.getActionOrStub(id))) {
        group.addActionId(id);
      }
    }
    return group;
  }

  private static Group createQuickListsGroup(final Condition<AnAction> filtered, final String filter, final boolean forceFiltering, final QuickList[] quickLists) {
    Arrays.sort(quickLists, new Comparator<QuickList>() {
      public int compare(QuickList l1, QuickList l2) {
        return l1.getActionId().compareTo(l2.getActionId());
      }
    });

    Group group = new Group(KeyMapBundle.message("quick.lists.group.title"), null, null);
    for (QuickList quickList : quickLists) {
      if (filtered != null && filtered.value(ActionManagerEx.getInstanceEx().getAction(quickList.getActionId()))) {
        group.addQuickList(quickList);
      } else if (SearchUtil.isComponentHighlighted(quickList.getDisplayName(), filter, forceFiltering, null)) {
        group.addQuickList(quickList);
      } else if (filtered == null && StringUtil.isEmpty(filter)) {
        group.addQuickList(quickList);
      }
    }
    return group;
  }


  private static Group createOtherGroup(Condition<AnAction> filtered, Group addedActions, final Keymap keymap) {
    addedActions.initIds();
    ArrayList<String> result = new ArrayList<String>();

    if (keymap != null) {
      String[] actionIds = keymap.getActionIds();
      for (String id : actionIds) {
        if (id.startsWith(EDITOR_PREFIX)) {
          AnAction action = ActionManager.getInstance().getActionOrStub("$" + id.substring(6));
          if (action != null) continue;
        }

        if (!id.startsWith(QuickList.QUICK_LIST_PREFIX) && !addedActions.containsId(id)) {
          result.add(id);
        }
      }
    }

    // add all registered actions
    final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    final KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
    String[] registeredActionIds = actionManager.getActionIds("");
    for (String id : registeredActionIds) {
      if (actionManager.getActionOrStub(id)instanceof ActionGroup) {
        continue;
      }
      if (id.startsWith(QuickList.QUICK_LIST_PREFIX) || addedActions.containsId(id) || result.contains(id)) {
        continue;
      }

      if (keymapManager.getBoundActions().contains(id)) continue;

      result.add(id);
    }

    filterOtherActionsGroup(result);

    ContainerUtil.quickSort(result, new Comparator<String>() {
      public int compare(String id1, String id2) {
        return getTextToCompare(id1).compareToIgnoreCase(getTextToCompare(id2));
      }
    });

    Group group = new Group(KeyMapBundle.message("other.group.title"), OTHER_ICON, OTHER_ICON);
    for (String id : result) {
      if (filtered == null || filtered.value(actionManager.getActionOrStub(id))) group.addActionId(id);
    }
    return group;
  }

  private static String getTextToCompare(String id) {
    AnAction action = ActionManager.getInstance().getActionOrStub(id);
    if (action == null) {
      return id;
    }
    String text = action.getTemplatePresentation().getText();
    return text != null ? text : id;
  }

  private static void filterOtherActionsGroup(ArrayList<String> actions) {
    filterOutGroup(actions, IdeActions.GROUP_GENERATE);
    filterOutGroup(actions, IdeActions.GROUP_NEW);
    filterOutGroup(actions, IdeActions.GROUP_CHANGE_SCHEME);
  }

  private static void filterOutGroup(ArrayList<String> actions, String groupId) {
    if (groupId == null) {
      throw new IllegalArgumentException();
    }
    ActionManager actionManager = ActionManager.getInstance();
    AnAction action = actionManager.getActionOrStub(groupId);
    if (action instanceof DefaultActionGroup) {
      DefaultActionGroup group = (DefaultActionGroup)action;
      AnAction[] children = group.getChildActionsOrStubs();
      for (AnAction child : children) {
        String childId = child instanceof ActionStub ? ((ActionStub)child).getId() : actionManager.getId(child);
        if (childId == null) {
          // SCR 35149
          continue;
        }
        if (child instanceof DefaultActionGroup) {
          filterOutGroup(actions, childId);
        }
        else {
          actions.remove(childId);
        }
      }
    }
  }

  public static DefaultMutableTreeNode createNode(Group group) {
    DefaultMutableTreeNode node = new DefaultMutableTreeNode(group);
    for (Object child : group.getChildren()) {
      if (child instanceof Group) {
        DefaultMutableTreeNode childNode = createNode((Group)child);
        node.add(childNode);
      }
      else {
        node.add(new DefaultMutableTreeNode(child));
      }
    }
    return node;
  }

  public static Group createMainGroup(final Project project, final Keymap keymap, final QuickList[] quickLists) {
    return createMainGroup(project, keymap, quickLists, null, false, null);
  }

  public static Group createMainGroup(final Project project,
                                      final Keymap keymap,
                                      final QuickList[] quickLists,
                                      final String filter,
                                      final boolean forceFiltering,
                                      final Condition<AnAction> filtered) {
    Group mainGroup = new Group(KeyMapBundle.message("all.actions.group.title"), null, null);
    mainGroup.addGroup(createEditorActionsGroup(filtered));
    mainGroup.addGroup(createMainMenuGroup(filtered));
    for (KeymapExtension extension : Extensions.getExtensions(KeymapExtension.EXTENSION_POINT_NAME)) {
      final Group group = createExtensionGroup(filtered, project, extension);
      if (group != null) {
        mainGroup.addGroup(group);
      }
    }
    mainGroup.addGroup(createMacrosGroup(filtered));
    mainGroup.addGroup(createQuickListsGroup(filtered, filter, forceFiltering, quickLists));
    final Group otherGroup = createOtherGroup(filtered, mainGroup, keymap);
    mainGroup.addGroup(otherGroup);
    mainGroup.addGroup(createPluginsActionsGroup(filtered, otherGroup));
    if (!StringUtil.isEmpty(filter) || filtered != null) {
      final ArrayList list = mainGroup.getChildren();
      for (Iterator i = list.iterator(); i.hasNext();) {
        final Object o = i.next();
        if (o instanceof Group) {
          final Group group = (Group)o;
          if (group.getSize() == 0) {
            if (!SearchUtil.isComponentHighlighted(group.getName(), filter, forceFiltering, null)) {
              i.remove();
            }
          }
        }
      }
    }
    return mainGroup;
  }

  public static Condition<AnAction> isActionFiltered(final String filter, final boolean force) {
    return new Condition<AnAction>() {
      public boolean value(final AnAction action) {
        if (filter == null) return true;
        if (action == null) return false;
        final String insensitiveFilter = filter.toLowerCase();
        final String text = action.getTemplatePresentation().getText();
        if (text != null) {
          final String lowerText = text.toLowerCase();
          if (SearchUtil
            .isComponentHighlighted(lowerText, insensitiveFilter, force, null)) {
            return true;
          }
          else if (lowerText.indexOf(insensitiveFilter) != -1) {
            return true;
          }
        }
        final String description = action.getTemplatePresentation().getDescription();
        if (description != null) {
          final String insensitiveDescription = description.toLowerCase();
          if (SearchUtil
            .isComponentHighlighted(insensitiveDescription, insensitiveFilter, force, null)) {
            return true;
          }
          else if (insensitiveDescription.indexOf(insensitiveFilter) != -1) {
            return true;
          }
        }
        return false;
      }
    };
  }

  public static Condition<AnAction> isActionFiltered(final ActionManager actionManager,
                                                     final Keymap keymap,
                                                     final KeyboardShortcut keyboardShortcut) {
    return new Condition<AnAction>() {
      public boolean value(final AnAction action) {
        if (keyboardShortcut == null) return true;
        if (action == null) return false;
        final Shortcut[] actionShortcuts =
          keymap.getShortcuts(action instanceof ActionStub ? ((ActionStub)action).getId() : actionManager.getId(action));
        for (Shortcut shortcut : actionShortcuts) {
          if (shortcut instanceof KeyboardShortcut) {
            final KeyboardShortcut keyboardActionShortcut = (KeyboardShortcut)shortcut;
            if (Comparing.equal(keyboardActionShortcut, keyboardShortcut)) {
              return true;
            }
          }
        }
        return false;
      }
    };
  }
}