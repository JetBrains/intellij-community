/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.options.ex;

import com.intellij.BundleBase;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.*;

/**
 * @author nik
 * @author Sergey.Malenkov
 */
public class ConfigurableExtensionPointUtil {

  private final static Logger LOG = Logger.getInstance(ConfigurableExtensionPointUtil.class);

  private ConfigurableExtensionPointUtil() {
  }


  public static List<Configurable> buildConfigurablesList(final ConfigurableEP<Configurable>[] extensions, @Nullable ConfigurableFilter filter) {
    final List<Configurable> result = new ArrayList<>();
    final Map<String, ConfigurableWrapper> idToConfigurable = ContainerUtil.newHashMap();
    List<String> idsInEpOrder = ContainerUtil.newArrayList();
    for (ConfigurableEP<Configurable> ep : extensions) {
      final Configurable configurable = ConfigurableWrapper.wrapConfigurable(ep);
      if (isSuppressed(configurable, filter)) continue;
      if (configurable instanceof ConfigurableWrapper) {
        final ConfigurableWrapper wrapper = (ConfigurableWrapper)configurable;
        idToConfigurable.put(wrapper.getId(), wrapper);
        idsInEpOrder.add(wrapper.getId());
      }
      else {
//        dumpConfigurable(configurablesExtensionPoint, ep, configurable);
        ContainerUtil.addIfNotNull(result, configurable);
      }
    }

    Set<String> visited = ContainerUtil.newHashSet();
    Map<String, List<String>> idTree = buildIdTree(idToConfigurable, idsInEpOrder);
    // modify configurables (append children)
    // Before adding a child to a parent, all children of the child should be already added to the child,
    //   because ConfigurableWrapper#addChild may return a new instance.
    for (final String id : idsInEpOrder) {
      addChildrenRec(id, idToConfigurable, visited, idTree);
    }
    // add roots only (i.e. configurables without parents)
    for (String id : idsInEpOrder) {
      ConfigurableWrapper wrapper = idToConfigurable.get(id);
      String parentId = wrapper.getParentId();
      if (parentId == null || !idToConfigurable.containsKey(parentId)) {
        result.add(wrapper);
      }
    }

    return result;
  }

  @NotNull
  private static ConfigurableWrapper addChildrenRec(@NotNull String id,
                                                    @NotNull Map<String, ConfigurableWrapper> idToConfigurable,
                                                    @NotNull Set<String> visited,
                                                    @NotNull Map<String, List<String>> idTree) {
    ConfigurableWrapper wrapper = idToConfigurable.get(id);
    if (visited.contains(id)) {
      return wrapper;
    }
    visited.add(id);
    List<String> childIds = idTree.get(id);
    if (childIds != null) {
      for (String childId : childIds) {
        ConfigurableWrapper childWrapper = addChildrenRec(childId, idToConfigurable, visited, idTree);
        wrapper = wrapper.addChild(childWrapper);
      }
      idToConfigurable.put(id, wrapper);
    }
    return wrapper;
  }

  @NotNull
  private static Map<String, List<String>> buildIdTree(@NotNull Map<String, ConfigurableWrapper> idToConfigurable,
                                                       @NotNull List<String> idsInEpOrder) {
    Map<String, List<String>> tree = ContainerUtil.newHashMap();
    for (String id : idsInEpOrder) {
      ConfigurableWrapper wrapper = idToConfigurable.get(id);
      String parentId = wrapper.getParentId();
      if (parentId != null) {
        ConfigurableWrapper parent = idToConfigurable.get(parentId);
        if (parent == null) {
          LOG.warn("Can't find parent for " + parentId + " (" + wrapper + ")");
          continue;
        }
        List<String> children = tree.get(parentId);
        if (children == null) {
          children = ContainerUtil.newArrayListWithCapacity(5);
          tree.put(parentId, children);
        }
        children.add(id);
      }
    }
    return tree;
  }

  /**
   * @param project         a project used to load project settings or {@code null}
   * @param withIdeSettings specifies whether to load application settings or not
   * @return the root configurable group that represents a tree of settings
   */
  public static ConfigurableGroup getConfigurableGroup(@Nullable Project project, boolean withIdeSettings) {
    if (!withIdeSettings && project == null) project = ProjectManager.getInstance().getDefaultProject();
    return getConfigurableGroup(getConfigurables(project, withIdeSettings), project);
  }

  /**
   * @param configurables a list of settings to process
   * @param project       a project used to create a project settings group or {@code null}
   * @return the root configurable group that represents a tree of settings
   */
  public static ConfigurableGroup getConfigurableGroup(@NotNull List<Configurable> configurables, @Nullable Project project) {
    Map<String, List<Configurable>> map = groupConfigurables(configurables);
    Map<String, Node<SortedConfigurableGroup>> tree = ContainerUtil.newHashMap();
    for (Map.Entry<String, List<Configurable>> entry : map.entrySet()) {
      addGroup(tree, project, entry.getKey(), entry.getValue(), null);
    }
    SortedConfigurableGroup root = getGroup(tree, "root");
    if (!tree.isEmpty()) {
      for (String groupId : tree.keySet()) {
        LOG.warn("ignore group: " + groupId);
      }
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
        @SuppressWarnings("unchecked") // expected type
        String childId = (String)iterator.next();
        node.myValue.myList.add(getGroup(tree, childId));
      }
    }
    return node.myValue;
  }

  private static void addGroup(Map<String, Node<SortedConfigurableGroup>> tree, Project project,
                               String groupId, List<Configurable> configurables, ResourceBundle alternative) {
    String id = "configurable.group." + groupId;
    ResourceBundle bundle = getBundle(id + ".settings.display.name", configurables, alternative);
    if (bundle == null) {
      bundle = OptionsBundle.getBundle();
      if ("root".equals(groupId)) {
        try {
          String value = bundle.getString("configurable.group.root.settings.display.name");
          LOG.error("OptionsBundle does not contain root group", value);
        }
        catch (Exception exception) {
          LOG.error("OptionsBundle does not contain root group", exception);
        }
      }
      else {
        LOG.warn("use other group instead of unexpected one: " + groupId);
        groupId = "other";
        id = "configurable.group." + groupId;
      }
    }
    Node<SortedConfigurableGroup> node = Node.get(tree, groupId);
    if (node.myValue == null) {
      int weight = getInt(bundle, id + ".settings.weight");
      String help = getString(bundle, id + ".settings.help.topic");
      String name = getString(bundle, id + ".settings.display.name");
      if (name != null && project != null) {
        if (!project.isDefault() && !name.contains("{")) {
          String named = getString(bundle, id + ".named.settings.display.name");
          name = named != null ? named : name;
        }
        if (name.contains("{")) {
          name = StringUtil.first(MessageFormat.format(name, project.getName()), 30, true);
        }
      }
      node.myValue = new SortedConfigurableGroup(id, name, help, weight);
    }
    if (configurables != null) {
      node.myValue.myList.addAll(configurables);
    }
    if (node.myParent == null && !groupId.equals("root")) {
      String parentId = getString(bundle, id + ".settings.parent");
      parentId = Node.cyclic(tree, parentId, "root", groupId, node);
      node.myParent = Node.add(tree, parentId, groupId);
      addGroup(tree, project, parentId, null, bundle);
    }
  }

  /**
   * @param configurables a list of settings to process
   * @return the map of different groups of settings
   */
  public static Map<String, List<Configurable>> groupConfigurables(@NotNull List<Configurable> configurables) {
    Map<String, Node<ConfigurableWrapper>> tree = new THashMap<>();
    for (Configurable configurable : configurables) {
      if (!(configurable instanceof ConfigurableWrapper)) {
        Node.add(tree, "other", configurable);
        continue;
      }

      ConfigurableWrapper wrapper = (ConfigurableWrapper)configurable;
      String id;
      try {
        id = wrapper.getId();
      }
      catch (Throwable e) {
        LOG.error("Cannot create configurable", e);
        continue;
      }

      Node<ConfigurableWrapper> node = Node.get(tree, id);
      if (node.myValue != null) {
        LOG.warn("ignore configurable with duplicated id: " + id);
        continue;
      }

      String parentId = wrapper.getParentId();
      String groupId = wrapper.getExtensionPoint().groupId;
      if (groupId != null) {
        if (parentId != null) {
          LOG.warn("ignore deprecated groupId: " + groupId + " for id: " + id);
        }
        else {
          //TODO:LOG.warn("use deprecated groupId: " + groupId + " for id: " + id);
          parentId = groupId;
        }
      }
      parentId = Node.cyclic(tree, parentId, "other", id, node);
      node.myParent = Node.add(tree, parentId, node);
      node.myValue = wrapper;
    }

    Map<String, List<Configurable>> map = new THashMap<>();
    for (String id : ArrayUtilRt.toStringArray(tree.keySet())) {
      Node<ConfigurableWrapper> node = tree.get(id);
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
  private static List<Configurable> getConfigurables(Map<String, Node<ConfigurableWrapper>> tree, Node<ConfigurableWrapper> node) {
    if (node.myChildren == null) {
      if (node.myValue == null) {
        return ContainerUtil.newArrayList(); // for group only
      }
      return null;
    }
    List<Configurable> list = ContainerUtil.newArrayListWithCapacity(node.myChildren.size());
    for (Iterator<Object> iterator = node.myChildren.iterator(); iterator.hasNext(); iterator.remove()) {
      Object child = iterator.next();
      if (child instanceof Configurable) {
        list.add((Configurable)child);
      }
      else {
        @SuppressWarnings("unchecked") // expected type
        Node<ConfigurableWrapper> value = (Node<ConfigurableWrapper>)child;
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
   * @param project         a project used to load project settings or {@code null}
   * @param withIdeSettings specifies whether to load application settings or not
   * @return the list of all valid settings according to parameters
   */
  private static List<Configurable> getConfigurables(@Nullable Project project, boolean withIdeSettings) {
    List<Configurable> list = ContainerUtil.newArrayList();
    if (withIdeSettings) {
      Application application = ApplicationManager.getApplication();
      if (application != null) {
        for (ConfigurableEP<Configurable> extension : application.getExtensions(Configurable.APPLICATION_CONFIGURABLE)) {
          addValid(list, ConfigurableWrapper.wrapConfigurable(extension, true), null);
        }
      }
    }
    if (project != null && !project.isDisposed()) {
      for (ConfigurableEP<Configurable> extension : project.getExtensions(Configurable.PROJECT_CONFIGURABLE)) {
        addValid(list, ConfigurableWrapper.wrapConfigurable(extension, true), project);
      }
    }
    return list;
  }

  private static void addValid(List<Configurable> list, Configurable configurable, Project project) {
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
    OptionalConfigurable optional = ConfigurableWrapper.cast(OptionalConfigurable.class, configurable);
    if (optional != null && !optional.needDisplay()) {
      return false;
    }
    return project == null || !project.isDefault() || !ConfigurableWrapper.isNonDefaultProject(configurable);
  }

  @Nullable
  public static ResourceBundle getBundle(@NotNull String resource,
                                         @Nullable Iterable<Configurable> configurables,
                                         @Nullable ResourceBundle alternative) {
    ResourceBundle bundle = OptionsBundle.getBundle();
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

  private static String getString(ResourceBundle bundle, String resource) {
    if (bundle == null) return null;
    try {
      // mimic CommonBundle.message(..) behavior
      return BundleBase.replaceMnemonicAmpersand(bundle.getString(resource));
    }
    catch (MissingResourceException ignored) {
      return null;
    }
  }

  private static int getInt(ResourceBundle bundle, String resource) {
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

  /*
  private static void dumpConfigurable(ExtensionPointName<ConfigurableEP<Configurable>> configurablesExtensionPoint,
                                       ConfigurableEP<Configurable> ep,
                                       Configurable configurable) {
    if (configurable != null && !(configurable instanceof ConfigurableGroup)) {
      if (ep.instanceClass != null && (configurable instanceof SearchableConfigurable) && (configurable instanceof Configurable.Composite)) {
        Element element = dump(ep, configurable, StringUtil.getShortName(configurablesExtensionPoint.getName()));
        final Configurable[] configurables = ((Configurable.Composite)configurable).getConfigurables();
        for (Configurable child : configurables) {
          final Element dump = dump(null, child, "configurable");
          element.addContent(dump);
        }
        final StringWriter out = new StringWriter();
        try {
          new XMLOutputter(Format.getPrettyFormat()).output(element, out);
        }
        catch (IOException e) {
        }
        System.out.println(out);
      }
    }
  }

  private static Element dump(@Nullable ConfigurableEP ep,
                              Configurable configurable, String name) {
    Element element = new Element(name);
    if (ep != null) {
      element.setAttribute("instance", ep.instanceClass);
      String id = ep.id == null ? ((SearchableConfigurable)configurable).getId() : ep.id;
      element.setAttribute("id", id);
    }
    else {
      element.setAttribute("instance", configurable.getClass().getName());
      if (configurable instanceof SearchableConfigurable) {
        element.setAttribute("id", ((SearchableConfigurable)configurable).getId());
      }
    }

    CommonBundle.lastKey = null;
    String displayName = configurable.getDisplayName();
    if (CommonBundle.lastKey != null) {
      element.setAttribute("key", CommonBundle.lastKey).setAttribute("bundle", CommonBundle.lastBundle);
    }
    else {
      element.setAttribute("displayName", displayName);
    }
    if (configurable instanceof NonDefaultProjectConfigurable) {
      element.setAttribute("nonDefaultProject", "true");
    }
    return element;
  }
  */

  /**
   * @deprecated create a new instance of configurable instead
   */
  @NotNull
  public static <T extends Configurable> T findProjectConfigurable(@NotNull Project project, @NotNull Class<T> configurableClass) {
    return findConfigurable(project.getExtensions(Configurable.PROJECT_CONFIGURABLE), configurableClass);
  }

  @NotNull
  public static <T extends Configurable> T findApplicationConfigurable(@NotNull Class<T> configurableClass) {
    return findConfigurable(Configurable.APPLICATION_CONFIGURABLE.getExtensions(), configurableClass);
  }

  @NotNull
  private static <T extends Configurable> T findConfigurable(ConfigurableEP<Configurable>[] extensions, Class<T> configurableClass) {
    for (ConfigurableEP<Configurable> extension : extensions) {
      if (extension.canCreateConfigurable()) {
        final Configurable configurable = extension.createConfigurable();
        if (configurableClass.isInstance(configurable)) {
          return configurableClass.cast(configurable);
        }
      }
    }
    throw new IllegalArgumentException("Cannot find configurable of " + configurableClass);
  }

  @Nullable
  public static Configurable createProjectConfigurableForProvider(@NotNull Project project, Class<? extends ConfigurableProvider> providerClass) {
    return createConfigurableForProvider(project.getExtensions(Configurable.PROJECT_CONFIGURABLE), providerClass);
  }

  @Nullable
  public static Configurable createApplicationConfigurableForProvider(Class<? extends ConfigurableProvider> providerClass) {
    return createConfigurableForProvider(Configurable.APPLICATION_CONFIGURABLE.getExtensions(), providerClass);
  }

  @Nullable
  private static Configurable createConfigurableForProvider(ConfigurableEP<Configurable>[] extensions, Class<? extends ConfigurableProvider> providerClass) {
    for (ConfigurableEP<Configurable> extension : extensions) {
      if (extension.providerClass != null) {
        final Class<Object> aClass = extension.findClassNoExceptions(extension.providerClass);
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
}
