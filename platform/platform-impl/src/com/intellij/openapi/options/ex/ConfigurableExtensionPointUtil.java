// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.ex;

import com.intellij.BundleBase;
import com.intellij.ide.actions.ConfigurablesPatcher;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.*;

import java.text.MessageFormat;
import java.util.*;

public final class ConfigurableExtensionPointUtil {
  private static final Logger LOG = Logger.getInstance(ConfigurableExtensionPointUtil.class);

  private ConfigurableExtensionPointUtil() {
  }

  public static @NotNull List<Configurable> buildConfigurablesList(@NotNull List<? extends ConfigurableEP<Configurable>> extensions, @Nullable ConfigurableFilter filter) {
    final List<Configurable> result = new ArrayList<>();
    final Map<String, HierarchicalConfigurable> idToConfigurable = new HashMap<>();
    List<String> idsInEpOrder = new ArrayList<>();
    for (ConfigurableEP<Configurable> ep : extensions) {
      Configurable configurable = ConfigurableWrapper.wrapConfigurable(ep);
      if (isSuppressed(configurable, filter)) {
        continue;
      }
      if (configurable instanceof HierarchicalConfigurable) {
        HierarchicalConfigurable wrapper = (HierarchicalConfigurable)configurable;
        idToConfigurable.put(wrapper.getId(), wrapper);
        idsInEpOrder.add(wrapper.getId());
      }
      else {
        //dumpConfigurable(configurablesExtensionPoint, ep, configurable);
        if (configurable != null) {
          result.add(configurable);
        }
      }
    }

    Set<String> visited = new HashSet<>();
    Map<String, List<String>> idTree = buildIdTree(idToConfigurable, idsInEpOrder);
    // modify configurables (append children)
    // Before adding a child to a parent, all children of the child should be already added to the child,
    //   because ConfigurableWrapper#addChild may return a new instance.
    for (final String id : idsInEpOrder) {
      addChildrenRec(id, idToConfigurable, visited, idTree);
    }
    // add roots only (i.e. configurables without parents)
    for (String id : idsInEpOrder) {
      HierarchicalConfigurable wrapper = idToConfigurable.get(id);
      String parentId = wrapper.getParentId();
      if (parentId == null || !idToConfigurable.containsKey(parentId)) {
        result.add(wrapper);
      }
    }

    return result;
  }

  private static @NotNull HierarchicalConfigurable addChildrenRec(@NotNull String id,
                                                                  @NotNull Map<String, HierarchicalConfigurable> idToConfigurable,
                                                                  @NotNull Set<? super String> visited,
                                                                  @NotNull Map<String, List<String>> idTree) {
    HierarchicalConfigurable wrapper = idToConfigurable.get(id);
    if (visited.contains(id)) {
      return wrapper;
    }
    visited.add(id);
    List<String> childIds = idTree.get(id);
    if (childIds != null) {
      for (String childId : childIds) {
        HierarchicalConfigurable childWrapper = addChildrenRec(childId, idToConfigurable, visited, idTree);
        wrapper = wrapper.addChild(childWrapper);
      }
      idToConfigurable.put(id, wrapper);
    }
    return wrapper;
  }

  private static @NotNull Map<String, List<String>> buildIdTree(@NotNull Map<String, HierarchicalConfigurable> idToConfigurable,
                                                                @NotNull List<String> idsInEpOrder) {
    Map<String, List<String>> tree = new HashMap<>();
    for (String id : idsInEpOrder) {
      HierarchicalConfigurable hierarchical = idToConfigurable.get(id);
      String parentId = hierarchical.getParentId();
      if (parentId != null) {
        HierarchicalConfigurable parent = idToConfigurable.get(parentId);
        if (parent == null) {
          LOG.warn("Can't find parent for " + parentId + " (" + hierarchical + ")");
          continue;
        }
        tree.computeIfAbsent(parentId, k -> new ArrayList<>(5)).add(id);
      }
    }
    return tree;
  }

  /**
   * @param project         a project used to load project settings or {@code null}
   * @param withIdeSettings specifies whether to load application settings or not
   * @return the root configurable group that represents a tree of settings
   */
  public static @NotNull ConfigurableGroup getConfigurableGroup(@Nullable Project project, boolean withIdeSettings) {
    Project targetProject = withIdeSettings ? project : ProjectUtil.currentOrDefaultProject(project);
    return new EpBasedConfigurableGroup(
      targetProject,
      () -> {
        List<Configurable> configurables = getConfigurables(targetProject, withIdeSettings);
        List<ConfigurablesPatcher> modificators = ConfigurablesPatcher.EP_NAME.getExtensionList();
        for (ConfigurablesPatcher modificator : modificators) {
          modificator.modifyOriginalConfigurablesList(configurables, targetProject);
        }
        return getConfigurableGroup(configurables, targetProject);
      }
    );
  }

  /**
   * @param configurables a list of settings to process
   * @param project       a project used to create a project settings group or {@code null}
   * @return the root configurable group that represents a tree of settings
   */
  public static @Nullable ConfigurableGroup getConfigurableGroup(@NotNull List<? extends Configurable> configurables, @Nullable Project project) {
    Map<String, List<Configurable>> map = groupConfigurables(configurables);
    Map<String, Node<SortedConfigurableGroup>> tree = new HashMap<>();
    for (Map.Entry<String, List<Configurable>> entry : map.entrySet()) {
      addGroup(tree, project, entry.getKey(), entry.getValue(), null);
    }

    SortedConfigurableGroup root = getGroup(tree, "root");
    if (!tree.isEmpty()) {
      LOG.warn("ignore groups: " + tree.keySet());
    }

    if (root != null && root.myList != null && Registry.is("ide.settings.replace.group.with.single.configurable")) {
      replaceGroupWithSingleConfigurable(root.myList);
    }
    return root;
  }

  private static void replaceGroupWithSingleConfigurable(List<Configurable> list) {
    for (int i = 0; i < list.size(); i++) {
      Configurable configurable = list.get(i);
      if (configurable instanceof SortedConfigurableGroup) {
        SortedConfigurableGroup group = (SortedConfigurableGroup)configurable;
        configurable = getConfigurableToReplace(group.myList, group.getWeight());
        if (configurable != null) {
          list.set(i, configurable);
        }
      }
    }
  }

  private static Configurable getConfigurableToReplace(List<Configurable> list, int weight) {
    if (list != null) {
      replaceGroupWithSingleConfigurable(list);
      if (1 == list.size()) {
        Configurable configurable = list.get(0);
        if (configurable instanceof SortedConfigurableGroup) {
          SortedConfigurableGroup group = (SortedConfigurableGroup)configurable;
          group.myWeight = weight; // modify weight according to the replacing group
          return group;
        }
        if (configurable instanceof ConfigurableWrapper) {
          ConfigurableWrapper wrapper = (ConfigurableWrapper)configurable;
          wrapper.myWeight = weight; // modify weight according to the replacing group
          return wrapper;
        }
      }
    }
    return null;
  }

  /**
   * @param tree    a map that represents a tree of nodes
   * @param groupId an identifier of a group to process children recursively
   * @return the tree of groups starting from the specified one
   */
  private static SortedConfigurableGroup getGroup(Map<String, Node<SortedConfigurableGroup>> tree, String groupId) {
    Node<SortedConfigurableGroup> node = tree.remove(groupId);
    if (node.myChildren != null) {
      for (Iterator<Object> iterator = node.myChildren.iterator(); iterator.hasNext(); iterator.remove()) {
        // expected type
        String childId = (String)iterator.next();
        node.myValue.myList.add(getGroup(tree, childId));
      }
    }
    return node.myValue;
  }

  private static void addGroup(@NotNull Map<String, Node<SortedConfigurableGroup>> tree, Project project,
                               String groupId, List<? extends Configurable> configurables, ResourceBundle alternative) {
    boolean root = "root".equals(groupId);
    ConfigurableGroupEP ep = root ? null : ConfigurableGroupEP.find(groupId);
    String id = "configurable.group." + groupId;
    ResourceBundle bundle = ep != null ? ep.getResourceBundle() : getBundle(id + ".settings.display.name", configurables, alternative);
    if (bundle == null) {
      bundle = OptionsBundle.INSTANCE.getResourceBundle();
      if (!root) {
        LOG.warn("use other group instead of unexpected one: " + groupId);
        groupId = "other";
        id = "configurable.group." + groupId;
      }
    }
    Node<SortedConfigurableGroup> node = Node.get(tree, groupId);
    if (node.myValue == null) {
      if (ep != null) {
        String name = project == null || project.isDefault() || !"project".equals(groupId)
                      ? ep.getDisplayName()
                      : StringUtil.first(MessageFormat.format(
                        ep.getResourceValue("configurable.group.project.named.settings.display.name"),
                        project.getName()), 30, true);
        node.myValue = new SortedConfigurableGroup(id, name, ep.getDescription(), ep.helpTopic, ep.weight);
      }
      else if (root) {
        node.myValue = new SortedConfigurableGroup(id, "ROOT GROUP", null, null, 0); //NON-NLS
      }
      else {
        LOG.warn("Use <groupConfigurable> to specify custom configurable group: " + groupId);
        int weight = getInt(bundle, id + ".settings.weight");
        String help = getString(bundle, id + ".settings.help.topic");
        String name = getName(bundle, id + ".settings.display.name");
        String desc = getString(bundle, id + ".settings.description");
        if (name != null && project != null) {
          if (!project.isDefault() && !name.contains("{")) {
            String named = getString(bundle, id + ".named.settings.display.name");
            name = named != null ? named : name;
          }
          if (name.contains("{")) {
            name = StringUtil.first(MessageFormat.format(name, project.getName()), 30, true);
          }
        }
        node.myValue = new SortedConfigurableGroup(id, name, desc, help, weight);
      }
    }
    if (configurables != null) {
      node.myValue.myList.addAll(configurables);
    }
    if (!root && node.myParent == null) {
      String parentId = ep != null ? ep.parentId : getString(bundle, id + ".settings.parent");
      parentId = Node.cyclic(tree, parentId, "root", groupId, node);
      node.myParent = Node.add(tree, parentId, groupId);
      addGroup(tree, project, parentId, null, bundle);
    }
  }

  /**
   * @param configurables a list of settings to process
   * @return the map of different groups of settings
   */
  public static @NotNull Map<String, List<Configurable>> groupConfigurables(@NotNull List<? extends Configurable> configurables) {
    Map<String, Node<HierarchicalConfigurable>> tree = new HashMap<>();
    for (Configurable configurable : configurables) {
      if (!(configurable instanceof HierarchicalConfigurable)) {
        Node.add(tree, "other", configurable);
        continue;
      }

      HierarchicalConfigurable hierarchical = (HierarchicalConfigurable)configurable;
      String id;
      try {
        id = hierarchical.getId();
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error("Cannot create configurable", e);
        continue;
      }

      Node<HierarchicalConfigurable> node = Node.get(tree, id);
      if (node.myValue != null) {
        LOG.warn("ignore configurable with duplicated id: " + id);
        continue;
      }

      String parentId = hierarchical.getParentId();

      if (hierarchical instanceof ConfigurableWrapper) {
        String groupId = ((ConfigurableWrapper)hierarchical).getExtensionPoint().groupId;
        if (groupId != null) {
          if (parentId != null) {
            LOG.warn("ignore deprecated groupId: " + groupId + " for id: " + id);
          }
          else {
            //TODO:LOG.warn("use deprecated groupId: " + groupId + " for id: " + id);
            parentId = groupId;
          }
        }
      }

      parentId = Node.cyclic(tree, parentId, "other", id, node);
      node.myParent = Node.add(tree, parentId, node);
      node.myValue = hierarchical;
    }

    Map<String, List<Configurable>> map = new HashMap<>();
    for (String id : ArrayUtilRt.toStringArray(tree.keySet())) {
      Node<HierarchicalConfigurable> node = tree.get(id);
      if (node != null) {
        List<Configurable> list = getConfigurables(tree, node);
        if (list != null) {
          map.put(id, list);
          tree.remove(id);
        }
      }
    }
    return map;
  }

  /**
   * @param tree a map that represents a tree of nodes
   * @param node a current node to process children recursively
   * @return the list of settings for a group or {@code null} for internal node
   */
  private static List<Configurable> getConfigurables(Map<String, Node<HierarchicalConfigurable>> tree,
                                                     Node<HierarchicalConfigurable> node) {
    if (node.myChildren == null) {
      if (node.myValue == null) {
        // for group only
        return new ArrayList<>();
      }
      return null;
    }

    List<Configurable> list = new ArrayList<>(node.myChildren.size());
    for (Iterator<Object> iterator = node.myChildren.iterator(); iterator.hasNext(); iterator.remove()) {
      Object child = iterator.next();
      if (child instanceof Configurable) {
        list.add((Configurable)child);
      }
      else {
        @SuppressWarnings("unchecked") // expected type
        Node<HierarchicalConfigurable> value = (Node<HierarchicalConfigurable>)child;
        if (getConfigurables(tree, value) != null) {
          throw new IllegalStateException("unexpected algorithm state");
        }
        list.add(value.myValue);
        tree.remove(value.myValue.getId());
      }
    }

    if (node.myValue == null) {
      return list; // for group only
    }

    for (Configurable configurable : list) {
      node.myValue = node.myValue.addChild(configurable);
    }
    return null;
  }

  /**
   * @param project         a project used to load project settings for or {@code null}
   * @param withIdeSettings specifies whether to load application settings or not
   * @return the list of all valid settings according to parameters
   */
  @ApiStatus.Internal
  public static @NotNull List<Configurable> getConfigurables(@Nullable Project project, boolean withIdeSettings) {
    List<Configurable> list = new ArrayList<>();
    if (withIdeSettings) {
      Application application = ApplicationManager.getApplication();
      if (application != null) {
        for (ConfigurableEP<Configurable> extension : Configurable.APPLICATION_CONFIGURABLE.getExtensionList()) {
          addValid(list, ConfigurableWrapper.wrapConfigurable(extension, true), null);
        }
      }
    }
    if (project != null && !project.isDisposed()) {
      for (ConfigurableEP<Configurable> extension : Configurable.PROJECT_CONFIGURABLE.getExtensions(project)) {
        addValid(list, ConfigurableWrapper.wrapConfigurable(extension, true), project);
      }
    }
    return list;
  }

  private static void addValid(@NotNull List<? super Configurable> list, Configurable configurable, Project project) {
    if (isValid(configurable, project)) {
      list.add(configurable);
    }
  }

  /**
   * @param configurable settings component to validate
   * @param project      current project, default template project or {@code null} for IDE settings
   * @return {@code true} if the specified configurable must be shown in the settings dialog
   */
  private static boolean isValid(Configurable configurable, Project project) {
    if (configurable == null) {
      return false;
    }
    return project == null || !project.isDefault() || !ConfigurableWrapper.isNonDefaultProject(configurable);
  }

  public static @Nullable ResourceBundle getBundle(@NonNls @NotNull String resource,
                                                   @Nullable Iterable<? extends Configurable> configurables,
                                                   @Nullable ResourceBundle alternative) {
    ResourceBundle bundle = OptionsBundle.INSTANCE.getResourceBundle();
    if (getString(bundle, resource) != null) {
      return bundle;
    }
    if (configurables != null) {
      for (Configurable configurable : configurables) {
        if (configurable instanceof ConfigurableWrapper) {
          ConfigurableWrapper wrapper = (ConfigurableWrapper)configurable;
          bundle = wrapper.getExtensionPoint().findBundle();
          if (getString(bundle, resource) != null) {
            return bundle;
          }
        }
      }
    }
    if (getString(alternative, resource) != null) {
      return alternative;
    }
    return null;
  }

  private static @Nls String getString(ResourceBundle bundle, @NonNls String resource) {
    if (bundle == null) return null;
    try {
      return bundle.getString(resource);
    }
    catch (MissingResourceException ignored) {
      return null;
    }
  }

  private static @Nls String getName(ResourceBundle bundle, @NonNls String resource) {
    if (bundle == null) return null;
    try {
      return BundleBase.messageOrDefault(bundle, resource, null);
    }
    catch (MissingResourceException ignored) {
      return null;
    }
  }

  private static int getInt(ResourceBundle bundle, @NonNls String resource) {
    try {
      String value = getString(bundle, resource);
      return value == null ? 0 : Integer.parseInt(value);
    }
    catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private static boolean isSuppressed(Configurable each, ConfigurableFilter filter) {
    return !isValid(each, null) || (filter != null && !filter.isIncluded(each));
  }

  public static @Nullable Configurable createProjectConfigurableForProvider(@NotNull Project project, Class<? extends ConfigurableProvider> providerClass) {
    return createConfigurableForProvider(Configurable.PROJECT_CONFIGURABLE.getIterable(project), providerClass);
  }

  public static @Nullable Configurable createApplicationConfigurableForProvider(Class<? extends ConfigurableProvider> providerClass) {
    return createConfigurableForProvider(Configurable.APPLICATION_CONFIGURABLE.getIterable(), providerClass);
  }

  private static @Nullable Configurable createConfigurableForProvider(@NotNull Iterable<? extends ConfigurableEP<Configurable>> extensions, Class<? extends ConfigurableProvider> providerClass) {
    for (ConfigurableEP<Configurable> extension : extensions) {
      if (extension.providerClass != null) {
        Class<?> aClass = extension.findClassOrNull(extension.providerClass);
        if (aClass != null && providerClass.isAssignableFrom(aClass)) {
          return extension.createConfigurable();
        }
      }
    }
    return null;
  }

  /**
   * Utility class that helps to build a tree.
   */
  private static final class Node<V> {
    List<Object> myChildren;
    Node<V> myParent;
    V myValue;

    private static <I, V> Node<V> get(@NotNull Map<I, Node<V>> tree, @NotNull I id) {
      Node<V> node = tree.get(id);
      if (node == null) {
        node = new Node<>();
        tree.put(id, node);
      }
      return node;
    }

    private static <I, V> Node<V> add(@NotNull Map<I, Node<V>> tree, @NotNull I id, Object child) {
      Node<V> node = get(tree, id);
      if (node.myChildren == null) {
        node.myChildren = new SmartList<>();
      }
      node.myChildren.add(child);
      return node;
    }

    private static <I, V> boolean cyclic(@NotNull Map<I, Node<V>> tree, @NotNull I id, Node<V> parent) {
      for (Node<V> node = tree.get(id); node != null; node = node.myParent) {
        if (node == parent) {
          return true;
        }
      }
      return false;
    }

    private static <I, V> I cyclic(@NotNull Map<I, Node<V>> tree, @Nullable I id, I idDefault, I idNode, Node<V> parent) {
      if (id == null) {
        id = idDefault;
      }
      if (cyclic(tree, id, parent)) {
        LOG.warn("ignore cyclic dependency: " + id + " cannot contain " + idNode);
        id = idDefault;
      }
      return id;
    }
  }

  @ApiStatus.Internal
  public static void patch(@NotNull String id, @Nullable String parentId, @Nullable String bundle) {
    ConfigurableGroupEP ep = ConfigurableGroupEP.find(id);
    if (ep != null) {
      ep.parentId = parentId;
      if (bundle != null) {
        ep.bundle = bundle;
      }
    }
  }
}
