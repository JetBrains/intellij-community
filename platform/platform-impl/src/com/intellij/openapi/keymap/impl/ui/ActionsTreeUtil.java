// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actionMacro.ActionMacro;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.customization.ActionUrl;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapExtension;
import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.ActionShortcutRestrictions;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.*;
import java.util.function.Supplier;

import static com.intellij.codeInsight.intention.IntentionShortcuts.WRAPPER_PREFIX;

public final class ActionsTreeUtil {
  private static final Logger LOG = Logger.getInstance(ActionsTreeUtil.class);

  @NonNls
  private static final String EDITOR_PREFIX = "Editor";

  private ActionsTreeUtil() {
  }

  public static @NotNull Map<String, String> createPluginActionsMap() {
    Set<PluginId> visited = new HashSet<>();
    Map<String, String> result = CollectionFactory.createSmallMemoryFootprintMap();
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
      PluginId id = descriptor.getPluginId();
      visited.add(id);
      if (PluginManagerCore.CORE_ID.equals(id)) {
        continue;
      }
      for (String actionId : actionManager.getPluginActions(id)) {
        result.put(actionId, descriptor.getName());
      }
    }
    for (PluginId id : PluginId.getRegisteredIds()) {
      if (visited.contains(id)) {
        continue;
      }
      for (String actionId : actionManager.getPluginActions(id)) {
        result.put(actionId, id.getIdString());
      }
    }
    return result;
  }

  private static @NotNull Group createPluginsActionsGroup(Condition<? super AnAction> filtered) {
    Group pluginsGroup = new Group(KeyMapBundle.message("plugins.group.title"), null, null);
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    JBIterable<IdeaPluginDescriptor> plugins = JBIterable.of(PluginManagerCore.getPlugins())
      .sort(Comparator.comparing(IdeaPluginDescriptor::getName));
    Map<PluginId, String> pluginNames = plugins.toMap(PluginDescriptor::getPluginId, PluginDescriptor::getName);
    List<PluginId> pluginsIds = plugins.map(PluginDescriptor::getPluginId)
      .append(JBIterable.from(PluginId.getRegisteredIds()).sort(PluginId::compareTo))
      .unique().toList();
    for (PluginId pluginId : pluginsIds) {
      if (PluginManagerCore.CORE_ID.equals(pluginId)) {
        continue;
      }
      String[] pluginActions = actionManager.getPluginActions(pluginId);
      if (pluginActions.length == 0) {
        continue;
      }
      //noinspection HardCodedStringLiteral
      String name = StringUtil.notNullize(pluginNames.get(pluginId), pluginId.getIdString());
      Group pluginGroup = createPluginActionsGroup(name, pluginActions, filtered);
      if (pluginGroup.getSize() > 0) {
        pluginsGroup.addGroup(pluginGroup);
      }
    }
    return pluginsGroup;
  }

  @NotNull
  private static Group createPluginActionsGroup(@NlsActions.ActionText String name,
                                                String[] pluginActions,
                                                Condition<? super AnAction> filtered) {
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    Group pluginGroup = new Group(name, null, null);
    Arrays.sort(pluginActions, Comparator.comparing(ActionsTreeUtil::getTextToCompare));
    for (String actionId : pluginActions) {
      AnAction action = actionManager.getActionOrStub(actionId);
      if (isNonExecutableActionGroup(actionId, action)) {
        continue;
      }
      if (filtered == null || filtered.value(action)) {
        pluginGroup.addActionId(actionId);
      }
    }
    return pluginGroup;
  }

  private static Group createMainMenuGroup(Condition<? super AnAction> filtered) {
    ActionManager actionManager = ActionManager.getInstance();
    Group group = new Group(getMainMenuTitle(), IdeActions.GROUP_MAIN_MENU, AllIcons.Nodes.KeymapMainMenu);
    ActionGroup mainMenuGroup = (ActionGroup)actionManager.getActionOrStub(IdeActions.GROUP_MAIN_MENU);
    fillGroupIgnorePopupFlag(mainMenuGroup, group, filtered, actionManager);
    return group;
  }

  private static @NotNull Condition<AnAction> wrapFilter(@Nullable final Condition<? super AnAction> filter, final Keymap keymap, final ActionManager actionManager) {
    final ActionShortcutRestrictions shortcutRestrictions = ActionShortcutRestrictions.getInstance();
    return action -> {
      if (action == null) return false;
      final String id = action instanceof ActionStub ? ((ActionStub)action).getId() : actionManager.getId(action);
      if (id != null) {
        if (!Registry.is("keymap.show.alias.actions")) {
          String binding = KeymapManagerEx.getInstanceEx().getActionBinding(id);
          boolean bound = binding != null
                          && actionManager.getAction(binding) != null // do not hide bound action, that miss the 'bound-with'
                          && !hasAssociatedShortcutsInHierarchy(id, keymap); // do not hide bound actions when they are redefined
          if (bound) {
            return false;
          }
        }
        if (!shortcutRestrictions.getForActionId(id).allowChanging) {
          return false;
        }
      }

      return filter == null || filter.value(action);
    };
  }

  private static boolean hasAssociatedShortcutsInHierarchy(String id, Keymap keymap) {
    while (keymap != null) {
      if (((KeymapImpl)keymap).hasOwnActionId(id)) return true;
      keymap = keymap.getParent();
    }
    return false;
  }

  private static void fillGroupIgnorePopupFlag(ActionGroup actionGroup,
                                               Group group,
                                               Condition<? super AnAction> filtered,
                                               ActionManager actionManager) {
    AnAction[] mainMenuTopGroups = getActions(actionGroup, actionManager);
    for (AnAction action : mainMenuTopGroups) {
      if (!(action instanceof ActionGroup)) continue;
      Group subGroup = createGroup((ActionGroup)action, false, filtered);
      if (subGroup.getSize() > 0) {
        group.addGroup(subGroup);
      }
    }
  }

  public static Group createGroup(ActionGroup actionGroup, boolean ignore, Condition<? super AnAction> filtered) {
    return createGroup(actionGroup, getName(actionGroup), actionGroup.getTemplatePresentation().getIcon(), null, ignore, filtered);
  }

  @NlsActions.ActionText
  private static String getName(AnAction action) {
    final String name = action.getTemplatePresentation().getText();
    if (name != null && !name.isEmpty()) {
      return name;
    }
    else {
      @NlsSafe final String id = action instanceof ActionStub ? ((ActionStub)action).getId() : ActionManager.getInstance().getId(action);
      if (id != null) {
        return id;
      }
      if (action instanceof DefaultActionGroup) {
        final DefaultActionGroup group = (DefaultActionGroup)action;
        if (group.getChildrenCount() == 0) return IdeBundle.message("action.empty.group.text");
        final AnAction[] children = group.getChildActionsOrStubs();
        for (AnAction child : children) {
          if (!(child instanceof Separator)) {
            return "group." + getName(child); //NON-NLS
          }
        }
        return IdeBundle.message("action.empty.unnamed.group.text");
      }
      return action.getClass().getName(); //NON-NLS
    }
  }

  public static Group createGroup(ActionGroup actionGroup,
                                  @NlsActions.ActionText String groupName,
                                  Icon icon,
                                  Icon openIcon,
                                  boolean ignore,
                                  Condition<? super AnAction> filtered) {
    return createGroup(actionGroup, groupName, icon, openIcon, ignore, filtered, true);
  }

  public static Group createGroup(ActionGroup actionGroup, @NlsActions.ActionText String groupName, Icon icon, Icon openIcon, boolean ignore, Condition<? super AnAction> filtered,
                                  boolean normalizeSeparators) {
    ActionManager actionManager = ActionManager.getInstance();
    Group group = new Group(groupName, actionManager.getId(actionGroup), icon);
    AnAction[] children = getActions(actionGroup, actionManager);

    for (AnAction action : children) {
      if (action == null) {
        LOG.error(groupName + " contains null actions");
        continue;
      }
      if (action instanceof ActionGroup) {
        Group subGroup = createGroup((ActionGroup)action, getName(action), null, null, ignore, filtered, normalizeSeparators);
        if (!ignore && !((ActionGroup)action).isPopup()) {
          group.addAll(subGroup);
        }
        else if (subGroup.getSize() > 0 ||
                 filtered == null || filtered.value(action)) {
          group.addGroup(subGroup);
        }
      }
      else if (action instanceof Separator) {
        group.addSeparator();
      }
      else {
        String id = action instanceof ActionStub ? ((ActionStub)action).getId() : actionManager.getId(action);
        if (id != null) {
          if (filtered == null || filtered.value(action)) {
            group.addActionId(id);
          }
        }
      }
    }
    if (normalizeSeparators) group.normalizeSeparators();
    return group;
  }

  @NotNull
  public static Group createCorrectedGroup(@NotNull ActionGroup actionGroup,
                                           @NotNull @NlsActions.ActionText String groupName,
                                           @NotNull List<? super String> path,
                                           @NotNull List<? extends ActionUrl> actionUrls) {
    path.add(groupName);

    ActionManager actionManager = ActionManager.getInstance();
    Group group = new Group(groupName, actionManager.getId(actionGroup), null);
    List<AnAction> children = ContainerUtil.newArrayList(getActions(actionGroup, actionManager));

    for (ActionUrl actionUrl : actionUrls) {
      if (areEqual(path, actionUrl)) { //actual path is shorter when we use custom root
        AnAction componentAction = actionUrl.getComponentAction();
        if (componentAction != null) {
          if (actionUrl.getActionType() == ActionUrl.ADDED) {
            if (children.size() > actionUrl.getAbsolutePosition()) {
              children.add(actionUrl.getAbsolutePosition(), componentAction);
            }
            else {
              children.add(componentAction);
            }
          }
          else if (actionUrl.getActionType() == ActionUrl.DELETED && children.size() > actionUrl.getAbsolutePosition()) {
            AnAction anAction = children.get(actionUrl.getAbsolutePosition());
            if (anAction.getTemplatePresentation().getText() == null
                ? (componentAction.getTemplatePresentation().getText() != null &&
                   componentAction.getTemplatePresentation().getText().length() > 0)
                : !anAction.getTemplatePresentation().getText().equals(componentAction.getTemplatePresentation().getText())) {
              continue;
            }
            children.remove(actionUrl.getAbsolutePosition());
          }
        }
      }
    }

    for (AnAction action : children) {
      if (action instanceof ActionGroup) {
        group.addGroup(createCorrectedGroup((ActionGroup)action, getName(action), path, actionUrls));
      }
      else if (action instanceof Separator) {
        group.addSeparator();
      }
      else {
        String id = action instanceof ActionStub ? ((ActionStub)action).getId() : actionManager.getId(action);
        if (id != null) {
          group.addActionId(id);
        }
      }
    }

    path.remove(path.size() - 1);

    return group;
  }

  private static boolean areEqual(@NotNull List<? super String> path, ActionUrl actionUrl) {
    ArrayList<String> groupPath = actionUrl.getGroupPath();
    if (path.size() > groupPath.size()) return false;
    for (int i = 0; i < path.size(); i++) {
      if (!Objects.equals(path.get(path.size() - 1 - i), groupPath.get(groupPath.size() - 1 - i)))
        return false;
    }
    return true;
  }

  private static Group createEditorActionsGroup(Condition<? super AnAction> filtered) {
    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup editorGroup = (DefaultActionGroup)actionManager.getActionOrStub(IdeActions.GROUP_EDITOR);
    if (editorGroup == null) throw new AssertionError(IdeActions.GROUP_EDITOR + " group not found");
    ArrayList<String> ids = new ArrayList<>();

    addEditorActions(filtered, editorGroup, ids);

    Collections.sort(ids);
    Group group = new Group(KeyMapBundle.message("editor.actions.group.title"), IdeActions.GROUP_EDITOR, AllIcons.Nodes.KeymapEditor);
    for (String id : ids) {
      group.addActionId(id);
    }

    return group;
  }

  private static void addEditorActions(final Condition<? super AnAction> filtered,
                                       final DefaultActionGroup editorGroup,
                                       final ArrayList<? super String> ids) {
    AnAction[] editorActions = editorGroup.getChildActionsOrStubs();
    final ActionManager actionManager = ActionManager.getInstance();
    for (AnAction editorAction : editorActions) {
      if (editorAction instanceof DefaultActionGroup) {
        addEditorActions(filtered, (DefaultActionGroup) editorAction, ids);
      }
      else {
        String actionId = editorAction instanceof ActionStub ? ((ActionStub)editorAction).getId() : actionManager.getId(editorAction);
        if (actionId == null) continue;
        if (filtered == null || filtered.value(editorAction)) {
          ids.add(actionId);
        }
      }
    }
  }

  private static Group createExtensionGroup(Condition<? super AnAction> filtered, final Project project, KeymapExtension provider) {
    return (Group) provider.createGroup(filtered, project);
  }

  private static Group createMacrosGroup(Condition<? super AnAction> filtered) {
    final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    List<String> ids = actionManager.getActionIdList(ActionMacro.MACRO_ACTION_PREFIX);
    ids.sort(null);
    Group group = new Group(KeyMapBundle.message("macros.group.title"), null, null);
    for (String id : ids) {
      if (filtered == null || filtered.value(actionManager.getActionOrStub(id))) {
        group.addActionId(id);
      }
    }
    return group;
  }

  private static Group createIntentionsGroup(Condition<? super AnAction> filtered) {
    final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    List<String> ids = actionManager.getActionIdList(WRAPPER_PREFIX);
    ids.sort(null);
    Group group = new Group(KeyMapBundle.message("intentions.group.title"), IdeActions.GROUP_INTENTIONS, null);
    for (String id : ids) {
      if (filtered == null || filtered.value(actionManager.getActionOrStub(id))) {
        group.addActionId(id);
      }
    }
    return group;
  }

  private static Group createQuickListsGroup(final Condition<? super AnAction> filtered, final String filter, final boolean forceFiltering, final QuickList[] quickLists) {
    Arrays.sort(quickLists, Comparator.comparing(QuickList::getActionId));

    Group group = new Group(KeyMapBundle.message("quick.lists.group.title"), null, null);
    for (QuickList quickList : quickLists) {
      if (filtered != null && filtered.value(ActionManagerEx.getInstanceEx().getAction(quickList.getActionId())) ||
          SearchUtil.isComponentHighlighted(quickList.getName(), filter, forceFiltering, null) ||
          filtered == null && StringUtil.isEmpty(filter)) {
        group.addQuickList(quickList);
      }
    }
    return group;
  }

  @NotNull
  private static Group createOtherGroup(@Nullable Condition<? super AnAction> filtered, Group mainGroup, @Nullable Keymap keymap) {
    mainGroup.initIds();
    Set<String> result = new HashSet<>();

    ActionManagerImpl actionManager = (ActionManagerImpl)ActionManagerEx.getInstanceEx();
    if (keymap != null) {
      for (String id : keymap.getActionIdList()) {
        if (id.startsWith(EDITOR_PREFIX) && actionManager.getActionOrStub("$" + id.substring(6)) != null) {
          continue;
        }

        if (!id.startsWith(QuickList.QUICK_LIST_PREFIX) && !mainGroup.containsId(id)) {
          result.add(id);
        }
      }
    }

    // add all registered actions
    List<String> namedGroups = new ArrayList<>();
    for (String id : actionManager.getActionIdList("")) {
      AnAction actionOrStub = actionManager.getActionOrStub(id);
      if (isNonExecutableActionGroup(id, actionOrStub) ||
          id.startsWith(QuickList.QUICK_LIST_PREFIX) ||
          mainGroup.containsId(id) ||
          result.contains(id)) {
        continue;
      }

      if (actionOrStub instanceof ActionGroup) {
        namedGroups.add(id);
      }
      else {
        result.add(id);
      }
    }

    filterOtherActionsGroup(result);

    Group group = new Group(KeyMapBundle.message("other.group.title"), AllIcons.Nodes.KeymapOther);
    for (AnAction action : getActions("Other.KeymapGroup")) {
      addAction(group, action, actionManager, filtered, false);
    }

    Set<String> groupIds = group.initIds();

    // a quick hack to skip already included groups
    JBTreeTraverser<String> traverser = JBTreeTraverser.from(o -> actionManager.getParentGroupIds(o));
    for (String actionId : namedGroups) {
      if (traverser.withRoot(actionId).unique().traverse()
        .filter(o -> mainGroup.containsId(o) || group.containsId(o)).isNotEmpty()) {
        continue;
      }
      result.add(actionId);
    }

    ContainerUtil.sort(group.getChildren(), Comparator.comparing(o -> ((Group)o).getName()));
    result.removeAll(groupIds);

    for (String id : ContainerUtil.sorted(result, Comparator.comparing(o -> getTextToCompare(o)))) {
      if (filtered == null || filtered.value(actionManager.getActionOrStub(id))) group.addActionId(id);
    }
    return group;
  }

  private static boolean isNonExecutableActionGroup(String id, AnAction actionOrStub) {
    return actionOrStub instanceof ActionGroup &&
           (((ActionGroup)actionOrStub).isPopup() ||
            StringUtil.isEmpty(actionOrStub.getTemplateText()) ||
            StringUtil.containsIgnoreCase(id, "Popup") ||
            StringUtil.containsIgnoreCase(id, "Toolbar"));
  }

  private static String getTextToCompare(String id) {
    AnAction action = ActionManager.getInstance().getActionOrStub(id);
    if (action == null) {
      return id;
    }
    String text = action.getTemplatePresentation().getText();
    return text != null ? text : id;
  }

  private static void filterOtherActionsGroup(Set<String> actions) {
    filterOutGroup(actions, IdeActions.GROUP_GENERATE);
    filterOutGroup(actions, IdeActions.GROUP_NEW);
    filterOutGroup(actions, IdeActions.GROUP_CHANGE_SCHEME);
  }

  private static void filterOutGroup(Set<String> actions, String groupId) {
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
        LOG.assertTrue(child != null);
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
                                      final Condition<? super AnAction> filtered) {
    final Condition<AnAction> wrappedFilter = wrapFilter(filtered, keymap, ActionManager.getInstance());
    Group mainGroup = new Group(KeyMapBundle.message("all.actions.group.title"), null, null);
    mainGroup.addGroup(createEditorActionsGroup(wrappedFilter));
    mainGroup.addGroup(createMainMenuGroup(wrappedFilter));
    for (KeymapExtension extension : KeymapExtension.EXTENSION_POINT_NAME.getExtensionList()) {
      final Group group = createExtensionGroup(wrappedFilter, project, extension);
      if (group != null) {
        mainGroup.addGroup(group);
      }
    }
    mainGroup.addGroup(createMacrosGroup(wrappedFilter));
    mainGroup.addGroup(createIntentionsGroup(wrappedFilter));
    mainGroup.addGroup(createQuickListsGroup(wrappedFilter, filter, forceFiltering, quickLists));
    mainGroup.addGroup(createPluginsActionsGroup(wrappedFilter));
    mainGroup.addGroup(createOtherGroup(wrappedFilter, mainGroup, keymap));
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

  private static Condition<AnAction> isActionFiltered(final String filter, final boolean force) {
    return action -> {
      if (filter == null) return true;
      if (action == null) return false;
      action = tryUnstubAction(action);

      final String insensitiveFilter = StringUtil.toLowerCase(filter);
      ArrayList<String> options = new ArrayList<>();
      options.add(action.getTemplatePresentation().getText());
      options.add(action.getTemplatePresentation().getDescription());
      for (Supplier<String> synonym : action.getSynonyms()) {
        options.add(synonym.get());
      }
      String id = action instanceof ActionStub ? ((ActionStub)action).getId() : ActionManager.getInstance().getId(action);
      if (id != null) {
        options.add(id);
        options.addAll(AbbreviationManager.getInstance().getAbbreviations(id));
      }

      for (String text : options) {
        if (text != null) {
          final String lowerText = StringUtil.toLowerCase(text);

          if (SearchUtil.isComponentHighlighted(lowerText, insensitiveFilter, force, null) || lowerText.contains(insensitiveFilter)) {
            return true;
          }
        }
      }
      return false;
    };
  }

  private static Condition<AnAction> isActionFiltered(final ActionManager actionManager,
                                                      final Keymap keymap,
                                                      final Shortcut shortcut) {
    return isActionFiltered(actionManager, keymap, sc -> sc != null && sc.startsWith(shortcut));
  }

  public static Condition<AnAction> isActionFiltered(final ActionManager actionManager,
                                                     final Keymap keymap,
                                                     final Condition<? super Shortcut> predicat) {
    return action -> {
      if (action == null) return false;
      final Shortcut[] actionShortcuts =
        keymap.getShortcuts(action instanceof ActionStub ? ((ActionStub)action).getId() : actionManager.getId(action));
      for (Shortcut actionShortcut : actionShortcuts) {
        if (predicat.value(actionShortcut))
          return true;
      }
      return false;
    };
  }

  public static Condition<AnAction> isActionFiltered(final ActionManager actionManager,
                                                     final Keymap keymap,
                                                     final Shortcut shortcut,
                                                     final String filter,
                                                     final boolean force) {
    return filter != null && filter.length() > 0 ? isActionFiltered(filter, force) :
           shortcut != null ? isActionFiltered(actionManager, keymap, shortcut) : null;
  }

  public static void addAction(KeymapGroup group, AnAction action, Condition<? super AnAction> filtered) {
    addAction(group, action, filtered, false);
  }

  public static void addAction(KeymapGroup group, AnAction action, Condition<? super AnAction> filtered, boolean forceNonPopup) {
    addAction(group, action, ActionManager.getInstance(), filtered, forceNonPopup);
  }

  private static void addAction(KeymapGroup group, AnAction action, ActionManager actionManager,
                                Condition<? super AnAction> filtered, boolean forceNonPopup) {
    if (action instanceof ActionGroup) {
      if (forceNonPopup) {
        AnAction[] actions = getActions((ActionGroup)action, actionManager);
        for (AnAction childAction : actions) {
          addAction(group, childAction, actionManager, filtered, true);
        }
      }
      else {
        Group subGroup = createGroup((ActionGroup)action, false, filtered);
        if (subGroup.getSize() > 0) {
          group.addGroup(subGroup);
        }
      }
    }
    else if (action instanceof Separator) {
      if (group instanceof Group) {
        ((Group)group).addSeparator();
      }
    }
    else {
      if (filtered == null || filtered.value(action)) {
        String id = action instanceof ActionStub ? ((ActionStub)action).getId() : actionManager.getId(action);
        group.addActionId(id);
      }
    }
  }

  public static AnAction @NotNull [] getActions(@NonNls String actionGroup) {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction group = actionManager.getActionOrStub(actionGroup);
    LOG.assertTrue(group instanceof ActionGroup, actionGroup + " is " + (group == null ? "not found" : "not a group"));
    return getActions((ActionGroup)group, actionManager);
  }

  private static AnAction @NotNull [] getActions(@NotNull ActionGroup group, @NotNull ActionManager actionManager) {
    // ActionManagerImpl#preloadActions does not preload action groups, e.g. File | New, so unstub it
    ActionGroup adjusted = group instanceof ActionGroupStub? (ActionGroup)actionManager.getAction(((ActionGroupStub)group).getId()) : group;
    return adjusted instanceof DefaultActionGroup
           ? ((DefaultActionGroup)adjusted).getChildActionsOrStubs()
           : adjusted.getChildren(null);
  }

  @NotNull
  private static AnAction tryUnstubAction(@NotNull AnAction action) {
    if (action instanceof ActionStub) {
      AnAction newAction = ActionManager.getInstance().getActionOrStub(((ActionStub)action).getId());
      if (newAction != null) return newAction;
    }
    return action;
  }

  @Nls
  public static String getMainMenuTitle() {
    return KeyMapBundle.message("main.menu.action.title");
  }

  @Nls
  public static String getMainToolbar() {
    return KeyMapBundle.message("main.toolbar.title");
  }

  @Nls
  public static String getExperimentalToolbar(){
    return KeyMapBundle.message("experimental.toolbar.title");
  }

  @Nls
  public static String getEditorPopup() {
    return KeyMapBundle.message("editor.popup.menu.title");
  }

  @Nls
  public static String getEditorGutterPopupMenu() {
    return KeyMapBundle.message("editor.gutter.popup.menu");
  }

  @Nls
  public static String getScopeViewPopupMenu() {
    return KeyMapBundle.message("scope.view.popup.menu");
  }

  @Nls
  public static String getNavigationBarPopupMenu() {
    return KeyMapBundle.message("navigation.bar.popup.menu");
  }

  @Nls
  public static String getNavigationBarToolbar() {
    return KeyMapBundle.message("navigation.bar.toolbar");
  }

  @Nls
  public static String getEditorTabPopup() {
    return KeyMapBundle.message("editor.tab.popup.menu.title");
  }

  @Nls
  public static String getFavoritesPopup() {
    return KeyMapBundle.message("favorites.popup.title");
  }

  @Nls
  public static String getProjectViewPopup() {
    return KeyMapBundle.message("project.view.popup.menu.title");
  }

  @Nls
  public static String getCommanderPopup() {
    return KeyMapBundle.message("commender.view.popup.menu.title");
  }

  @Nls
  public static String getJ2EEPopup() {
    return KeyMapBundle.message("j2ee.view.popup.menu.title");
  }
}
