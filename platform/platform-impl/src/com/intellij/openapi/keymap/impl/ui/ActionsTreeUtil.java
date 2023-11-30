// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.diagnostic.PluginException;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actionMacro.ActionMacro;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.customization.ActionUrl;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.impl.ActionGroupStub;
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
import com.intellij.openapi.util.text.Strings;
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
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.intellij.codeInsight.intention.IntentionShortcuts.WRAPPER_PREFIX;

public final class ActionsTreeUtil {
  private static final Logger LOG = Logger.getInstance(ActionsTreeUtil.class);

  private static final @NonNls String EDITOR_PREFIX = "Editor";

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
    Group pluginsGroup = new Group(KeyMapBundle.message("plugins.group.title"), null);
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    JBIterable<IdeaPluginDescriptor> plugins = JBIterable.of(PluginManagerCore.getPlugins())
      .sort(Comparator.comparing(IdeaPluginDescriptor::getName));
    Map<PluginId, String> pluginNames = plugins.toMap(PluginDescriptor::getPluginId, PluginDescriptor::getName);
    List<PluginId> pluginsIds = plugins.map(PluginDescriptor::getPluginId)
      .append(JBIterable.from(PluginId.getRegisteredIds()).sort(PluginId::compareTo))
      .unique().toList();
    for (PluginId pluginId : pluginsIds) {
      if (PluginManagerCore.CORE_ID.equals(pluginId)
          || ContainerUtil.exists(KeymapExtension.EXTENSION_POINT_NAME.getExtensionList(), e -> e.skipPluginGroup(pluginId))) {
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

  private static @NotNull Group createPluginActionsGroup(@NlsActions.ActionText String name,
                                                         String[] pluginActions,
                                                         Condition<? super AnAction> filtered) {
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    Group pluginGroup = new Group(name, null);
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

  private static @NotNull Condition<AnAction> wrapFilter(final @Nullable Condition<? super AnAction> filter,
                                                         final Keymap keymap,
                                                         final ActionManager actionManager) {
    final ActionShortcutRestrictions shortcutRestrictions = ActionShortcutRestrictions.getInstance();
    return action -> {
      if (action == null) return false;
      final String id = actionManager.getId(action);
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

  public static Group createGroup(ActionGroup actionGroup, boolean forceAsPopup, Condition<? super AnAction> filtered) {
    String groupName = getName(actionGroup);
    return createGroup(actionGroup, groupName, actionGroup.getTemplatePresentation().getIconSupplier(), forceAsPopup, filtered, true);
  }

  public static @NlsActions.ActionText String getName(AnAction action) {
    String name = action.getTemplateText();
    if (name != null && !name.isEmpty()) {
      return name;
    }

    @NlsSafe String id = ActionManager.getInstance().getId(action);
    if (id != null) {
      return id;
    }

    if (action instanceof DefaultActionGroup group) {
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

  public static Group createGroup(ActionGroup actionGroup,
                                  @NlsActions.ActionText String groupName,
                                  Icon icon,
                                  boolean forceAsPopup,
                                  Condition<? super AnAction> filtered) {
    return createGroup(actionGroup, groupName, () -> icon, forceAsPopup, filtered, true);
  }

  public static Group createGroup(ActionGroup actionGroup,
                                  @NlsActions.ActionText String groupName,
                                  @Nullable Supplier<? extends @Nullable Icon> icon,
                                  boolean forceAsPopup,
                                  Predicate<? super AnAction> filtered,
                                  boolean normalizeSeparators) {
    ActionManager actionManager = ActionManager.getInstance();
    Group group = new Group(groupName, actionManager.getId(actionGroup), icon);
    AnAction[] children = getActions(actionGroup, actionManager);
    for (AnAction action : children) {
      if (action == null) {
        LOG.error(groupName + " contains null actions");
        continue;
      }
      if (action instanceof ActionGroup childGroup) {
        Group subGroup = createGroup(childGroup, getName(action), null, forceAsPopup, filtered, normalizeSeparators);
        if (forceAsPopup || childGroup.isPopup() || !Strings.isEmpty(childGroup.getTemplateText())) {
          if (subGroup.getSize() > 0 || filtered == null || filtered.test(childGroup)) {
            group.addGroup(subGroup);
          }
        }
        else {
          group.addAll(subGroup);
        }
      }
      else if (action instanceof Separator) {
        if (filtered == null || filtered.test(action)) {
          group.addSeparator();
        }
      }
      else {
        String id = actionManager.getId(action);
        if (id != null) {
          if (filtered == null || filtered.test(action)) {
            group.addActionId(id);
          }
        }
      }
    }
    if (normalizeSeparators) group.normalizeSeparators();
    return group;
  }

  public static @NotNull Group createCorrectedGroup(@NotNull ActionGroup actionGroup,
                                                    @NotNull @NlsActions.ActionText String groupName,
                                                    @NotNull List<? super String> path,
                                                    @NotNull List<ActionUrl> actionUrls) {
    path.add(groupName);

    ActionManager actionManager = ActionManager.getInstance();
    String groupId = actionManager.getId(actionGroup);
    Group group = new Group(groupName, groupId, actionGroup.getTemplatePresentation().getIcon());
    List<AnAction> children = new ArrayList<>(Arrays.asList(getActions(actionGroup, actionManager)));

    for (ActionUrl actionUrl : actionUrls) {
      Object component = actionUrl.getComponent();
      if (component instanceof Group correctedGroup && Objects.equals(correctedGroup.getId(), groupId)) {
        group.setForceShowAsPopup(correctedGroup.isForceShowAsPopup());
      }
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
            if (anAction.getTemplateText() == null
                ? (componentAction.getTemplateText() != null &&
                   !componentAction.getTemplateText().isEmpty())
                : !anAction.getTemplateText().equals(componentAction.getTemplateText())) {
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
        String id = actionManager.getId(action);
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
      if (!Objects.equals(path.get(path.size() - 1 - i), groupPath.get(groupPath.size() - 1 - i))) {
        return false;
      }
    }
    return true;
  }

  private static Group createEditorActionsGroup(Predicate<? super AnAction> filtered) {
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

  private static void addEditorActions(Predicate<? super AnAction> filtered,
                                       final DefaultActionGroup editorGroup,
                                       final ArrayList<? super String> ids) {
    AnAction[] editorActions = editorGroup.getChildActionsOrStubs();
    final ActionManager actionManager = ActionManager.getInstance();
    for (AnAction editorAction : editorActions) {
      if (editorAction instanceof DefaultActionGroup) {
        addEditorActions(filtered, (DefaultActionGroup)editorAction, ids);
      }
      else {
        String actionId = actionManager.getId(editorAction);
        if (actionId == null) {
          continue;
        }
        if (filtered == null || filtered.test(editorAction)) {
          ids.add(actionId);
        }
      }
    }
  }

  private static Group createExtensionGroup(Condition<? super AnAction> filtered, final Project project, KeymapExtension provider) {
    return (Group)provider.createGroup(filtered, project);
  }

  private static Group createMacrosGroup(Condition<? super AnAction> filtered) {
    final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    List<String> ids = actionManager.getActionIdList(ActionMacro.MACRO_ACTION_PREFIX);
    ids.sort(null);
    Group group = new Group(KeyMapBundle.message("macros.group.title"), null, (Supplier<? extends Icon>)null);
    for (String id : ids) {
      if (filtered == null || filtered.value(actionManager.getActionOrStub(id))) {
        group.addActionId(id);
      }
    }
    return group;
  }

  private static Group createIntentionsGroup(Condition<? super AnAction> filtered) {
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    List<String> ids = actionManager.getActionIdList(WRAPPER_PREFIX);
    ids.sort(null);
    Group group = new Group(KeyMapBundle.message("intentions.group.title"), IdeActions.GROUP_INTENTIONS, (Supplier<? extends Icon>)null);
    for (String id : ids) {
      if (filtered == null || filtered.value(actionManager.getActionOrStub(id))) {
        group.addActionId(id);
      }
    }
    return group;
  }

  private static Group createQuickListsGroup(final Condition<? super AnAction> filtered,
                                             final String filter,
                                             final boolean forceFiltering,
                                             final QuickList[] quickLists) {
    Arrays.sort(quickLists, Comparator.comparing(QuickList::getActionId));

    Group group = new Group(KeyMapBundle.message("quick.lists.group.title"));
    for (QuickList quickList : quickLists) {
      if (filtered != null && filtered.value(ActionManagerEx.getInstanceEx().getAction(quickList.getActionId())) ||
          SearchUtil.isComponentHighlighted(quickList.getName(), filter, forceFiltering, null) ||
          filtered == null && StringUtil.isEmpty(filter)) {
        group.addQuickList(quickList);
      }
    }
    return group;
  }

  private static @NotNull Group createOtherGroup(@Nullable Condition<? super AnAction> filtered, Group mainGroup, @Nullable Keymap keymap) {
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

    Group group = new Group(KeyMapBundle.message("other.group.title"), null, () -> AllIcons.Nodes.KeymapOther);
    for (AnAction action : getActions("Other.KeymapGroup")) {
      addAction(group, action, actionManager, filtered, false, false);
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
      AnAction actionOrStub = actionManager.getActionOrStub(id);
      if (actionOrStub == null || isSearchable(actionOrStub)) {
        if (filtered == null || filtered.value(actionOrStub)) {
          group.addActionId(id);
        }
      }
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

  public static String getTextToCompare(String id) {
    AnAction action = ActionManager.getInstance().getActionOrStub(id);
    if (action == null) {
      return id;
    }
    String text = action.getTemplateText();
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
    if (action instanceof DefaultActionGroup group) {
      AnAction[] children = group.getChildActionsOrStubs();
      for (AnAction child : children) {
        String childId = actionManager.getId(child);
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
    Group mainGroup = new Group(KeyMapBundle.message("all.actions.group.title"));
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
      final ArrayList<Object> list = mainGroup.getChildren();
      for (Iterator<Object> i = list.iterator(); i.hasNext(); ) {
        final Object o = i.next();
        if (o instanceof Group group) {
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
      options.add(action.getTemplateText());
      options.add(action.getTemplatePresentation().getDescription());
      for (Supplier<String> synonym : action.getSynonyms()) {
        options.add(synonym.get());
      }
      String id = ActionManager.getInstance().getId(action);
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
      final Shortcut[] actionShortcuts = keymap.getShortcuts(actionManager.getId(action));
      for (Shortcut actionShortcut : actionShortcuts) {
        if (predicat.value(actionShortcut)) {
          return true;
        }
      }
      return false;
    };
  }

  public static Condition<AnAction> isActionFiltered(final ActionManager actionManager,
                                                     final Keymap keymap,
                                                     final Shortcut shortcut,
                                                     final String filter,
                                                     final boolean force) {
    return filter != null && !filter.isEmpty() ? isActionFiltered(filter, force) :
           shortcut != null ? isActionFiltered(actionManager, keymap, shortcut) : null;
  }

  public static void addAction(KeymapGroup group, AnAction action, Condition<? super AnAction> filtered) {
    addAction(group, action, filtered, false);
  }

  public static void addAction(KeymapGroup group, AnAction action, Condition<? super AnAction> filtered, boolean forceNonPopup) {
    addAction(group, action, ActionManager.getInstance(), filtered, forceNonPopup, false);
  }

  public static void addAction(KeymapGroup group, AnAction action, ActionManager actionManager,
                               Condition<? super AnAction> filtered, boolean forceNonPopup, boolean skipUnnamedGroups) {
    if (action instanceof ActionGroup) {
      if (forceNonPopup || skipUnnamedGroups) {
        String text = action.getTemplateText();
        boolean skip = forceNonPopup || StringUtil.isEmpty(text);
        KeymapGroup tgtGroup;
        if (skip) {
          tgtGroup = group;
        }
        else {
          tgtGroup = new Group(text, actionManager.getId(action), action.getTemplatePresentation().getIcon());
          group.addGroup(tgtGroup);
        }
        AnAction[] actions = getActions((ActionGroup)action, actionManager);
        for (AnAction childAction : actions) {
          addAction(tgtGroup, childAction, actionManager, filtered, forceNonPopup, skipUnnamedGroups);
        }
        ((Group)tgtGroup).normalizeSeparators();
      }
      else {
        Group subGroup = createGroup((ActionGroup)action, false, filtered);
        if (subGroup.getSize() > 0) {
          group.addGroup(subGroup);
        }
      }
    }
    else if (action instanceof Separator) {
      if (group instanceof Group && (filtered == null || filtered.value(action))) {
        ((Group)group).addSeparator();
      }
    }
    else {
      if (filtered == null || filtered.value(action)) {
        String id = actionManager.getId(action);
        if (id != null) group.addActionId(id);
      }
    }
  }

  private static boolean isSearchable(@NotNull AnAction action) {
    if (action instanceof ActionGroup) {
      return ((ActionGroup)action).isSearchable();
    }
    return true;
  }

  public static AnAction @NotNull [] getActions(@NonNls String actionGroup) {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction group = actionManager.getActionOrStub(actionGroup);
    if (group == null) {
      LOG.error(actionGroup + " not found");
      return AnAction.EMPTY_ARRAY;
    }
    else if (!(group instanceof ActionGroup)) {
      PluginException.logPluginError(LOG, actionGroup + " is not a group", null, group.getClass());
      return AnAction.EMPTY_ARRAY;
    }
    return getActions((ActionGroup)group, actionManager);
  }

  private static AnAction @NotNull [] getActions(@NotNull ActionGroup group, @NotNull ActionManager actionManager) {
    // ActionManagerImpl#preloadActions does not preload action groups, e.g., File | New, so unstub it
    ActionGroup adjusted = group;
    if (group instanceof ActionGroupStub) {
      String id = ((ActionGroupStub)group).getId();
      AnAction action = actionManager.getAction(id);
      if (action instanceof ActionGroup) {
        adjusted = (ActionGroup)action;
      }
      else {
        PluginException.logPluginError(LOG, "not an ActionGroup: " + action + " id=" + id, null, action.getClass());
      }
    }
    try {
      return adjusted instanceof DefaultActionGroup
             ? ((DefaultActionGroup)adjusted).getChildActionsOrStubs()
             : adjusted.getChildren(null);
    }
    catch (Throwable e) {
      return AnAction.EMPTY_ARRAY;
    }
  }

  private static @NotNull AnAction tryUnstubAction(@NotNull AnAction action) {
    if (action instanceof ActionStub) {
      AnAction newAction = ActionManager.getInstance().getActionOrStub(((ActionStub)action).getId());
      if (newAction != null) return newAction;
    }
    return action;
  }

  public static @Nls String getMainMenuTitle() {
    return KeyMapBundle.message("main.menu.action.title");
  }

  public static @Nls String getMainToolbar() {
    return KeyMapBundle.message("main.toolbar.title");
  }

  public static @Nls String getMainToolbarLeft() {
    return ActionsBundle.message("group.MainToolbarLeft.text");
  }

  public static @Nls String getMainToolbarCenter() {
    return ActionsBundle.message("group.MainToolbarCenter.text");
  }

  public static @Nls String getMainToolbarRight() {
    return ActionsBundle.message("group.MainToolbarRight.text");
  }

  public static @Nls String getExperimentalToolbar() {
    return KeyMapBundle.message("experimental.toolbar.title");
  }

  public static @Nls String getExperimentalToolbarXamarin() {
    return KeyMapBundle.message("experimental.toolbar.xamarin.title");
  }

  public static @Nls String getEditorPopup() {
    return KeyMapBundle.message("editor.popup.menu.title");
  }

  public static @Nls String getEditorGutterPopupMenu() {
    return KeyMapBundle.message("editor.gutter.popup.menu");
  }

  public static @Nls String getScopeViewPopupMenu() {
    return KeyMapBundle.message("scope.view.popup.menu");
  }

  public static @Nls String getNavigationBarPopupMenu() {
    return KeyMapBundle.message("navigation.bar.popup.menu");
  }

  public static @Nls String getNavigationBarToolbar() {
    return KeyMapBundle.message("navigation.bar.toolbar");
  }

  public static @Nls String getEditorTabPopup() {
    return KeyMapBundle.message("editor.tab.popup.menu.title");
  }


  public static @Nls String getProjectViewPopup() {
    return KeyMapBundle.message("project.view.popup.menu.title");
  }
}
