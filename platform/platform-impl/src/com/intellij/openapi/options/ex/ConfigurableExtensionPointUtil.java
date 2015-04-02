/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.openapi.options.OptionalConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class ConfigurableExtensionPointUtil {

  private final static Logger LOG = Logger.getInstance(ConfigurableExtensionPointUtil.class);

  private ConfigurableExtensionPointUtil() {
  }


  public static List<Configurable> buildConfigurablesList(final ConfigurableEP<Configurable>[] extensions,
                                                          final Configurable[] components,
                                                          @Nullable ConfigurableFilter filter) {
    final List<Configurable> result = new ArrayList<Configurable>();
    for (Configurable component : components) {
      if (!isSuppressed(component, filter)) {
        result.add(component);
      }
    }

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
        ContainerUtil.addIfNotNull(configurable, result);
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
    Map<String, List<Configurable>> map = groupConfigurables(getConfigurables(project, withIdeSettings, true));
    return new SortedConfigurableGroup(project, map);
  }

  /**
   * @param configurables a list of settings to process
   * @return the map of different groups of settings
   */
  public static Map<String, List<Configurable>> groupConfigurables(@NotNull List<Configurable> configurables) {
    Map<String, Node<ConfigurableWrapper>> tree = ContainerUtil.newHashMap();
    for (Configurable configurable : configurables) {
      if (configurable instanceof ConfigurableWrapper) {
        ConfigurableWrapper wrapper = (ConfigurableWrapper)configurable;
        String id = wrapper.getId();
        Node<ConfigurableWrapper> node = Node.get(tree, id);
        if (node.myValue != null) {
          LOG.warn("ignore configurable with duplicated id: " + id);
        }
        else {
          String parentId = wrapper.getParentId();
          String groupId = wrapper.getExtensionPoint().groupId;
          if (groupId != null) {
            if (parentId != null) {
              LOG.warn("ignore deprecated groupId: " + groupId + " for id: " + id);
            }
            else {
              LOG.warn("use deprecated groupId instead of parentId: " + groupId + " for id: " + id);
              parentId = groupId;
            }
          }
          if (Node.cyclic(tree, parentId, node)) {
            LOG.warn("ignore cyclic dependency: " + parentId + " cannot contain " + id);
            parentId = null;
          }
          node.myParent = Node.add(tree, parentId, node);
          node.myValue = wrapper;
        }
      }
      else {
        Node.add(tree, null, configurable);
      }
    }
    Map<String, List<Configurable>> map = ContainerUtil.newHashMap();
    for (String id : tree.keySet().toArray(new String[tree.size()])) {
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
   * @param loadComponents  specifies whether to load Configurable components or not
   * @return the list of all valid settings according to parameters
   */
  private static List<Configurable> getConfigurables(@Nullable Project project, boolean withIdeSettings, boolean loadComponents) {
    List<Configurable> list = ContainerUtil.newArrayList();
    if (withIdeSettings) {
      Application application = ApplicationManager.getApplication();
      if (application != null) {
        if (loadComponents) {
          addValid(list, application.getComponents(Configurable.class), null);
        }
        for (ConfigurableEP<Configurable> extension : application.getExtensions(Configurable.APPLICATION_CONFIGURABLE)) {
          addValid(list, ConfigurableWrapper.wrapConfigurable(extension), null);
        }
      }
    }
    if (project != null) {
      if (loadComponents) {
        addValid(list, project.getComponents(Configurable.class), project);
      }
      for (ConfigurableEP<Configurable> extension : project.getExtensions(Configurable.PROJECT_CONFIGURABLE)) {
        addValid(list, ConfigurableWrapper.wrapConfigurable(extension), project);
      }
    }
    return list;
  }

  private static void addValid(List<Configurable> list, Configurable configurable, Project project) {
    if (isValid(configurable, project)) {
      list.add(configurable);
    }
  }

  private static void addValid(List<Configurable> list, Configurable[] configurables, Project project) {
    for (Configurable configurable : configurables) {
      addValid(list, configurable, project);
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
    if (ConfigurableWrapper.cast(Configurable.Assistant.class, configurable) != null) {
      return false;
    }
    OptionalConfigurable optional = ConfigurableWrapper.cast(OptionalConfigurable.class, configurable);
    if (optional != null && !optional.needDisplay()) {
      return false;
    }
    return project == null || !project.isDefault() || !ConfigurableWrapper.isNonDefaultProject(configurable);
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
    List<Object> myChildren = ContainerUtil.newArrayList();
    Node<V> myParent;
    V myValue;

    private static <I, V> Node<V> get(Map<I, Node<V>> tree, I id) {
      Node<V> node = tree.get(id);
      if (node == null) {
        node = new Node<V>();
        tree.put(id, node);
      }
      return node;
    }

    private static <I, V> Node<V> add(Map<I, Node<V>> tree, I id, Object child) {
      Node<V> node = get(tree, id);
      node.myChildren.add(child);
      return node;
    }

    private static <I, V> boolean cyclic(Map<I, Node<V>> tree, I id, Node<V> parent) {
      for (Node<V> node = tree.get(id); node != null; node = node.myParent) {
        if (node == parent) {
          return true;
        }
      }
      return false;
    }
  }
}
