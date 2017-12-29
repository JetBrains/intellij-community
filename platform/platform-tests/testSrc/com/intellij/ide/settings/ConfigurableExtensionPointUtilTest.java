/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.settings;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil;
import com.intellij.openapi.options.ex.ConfigurableFilter;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.JdomKt;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Nikolay Matveev
 * @author Sergey.Malenkov
 */
public class ConfigurableExtensionPointUtilTest extends LightPlatformTestCase {

  public void testSimpleTree() throws Exception {
    matchStructures(
      ContainerUtil.newArrayList(
        createConfigurable(null, "A"),
        createConfigurable("A", "B"),
        createConfigurable("A", "C"),
        createConfigurable("C", "D")
      ),
      null,
      ContainerUtil.newArrayList(node("A",
                                      node("B"),
                                      node("C",
                                           node("D")
                                      )
                                 )
      )
    );
  }

  public void testComplexTree() throws Exception {
    matchStructures(
      ContainerUtil.newArrayList(
        createConfigurable(null, "first"),
        createConfigurable("first", "first.first"),
        createConfigurable("first.first", "first.first.first"),
        createConfigurable("first.first", "first.first.second"),
        createConfigurable("first.first.second", "first.first.second.first"),
        createConfigurable("first.first.second", "first.first.second.second"),
        createConfigurable("first.first", "first.first.third"),
        createConfigurable("first.first", "first.first.fourth"),
        createConfigurable("first", "first.second"),
        createConfigurable("first.second", "first.second.first"),
        createConfigurable("first.second.first", "first.second.first.first"),
        createConfigurable("first.second.first", "first.second.first.second"),
        createConfigurable("first.second.first", "first.second.first.third"),
        createConfigurable("first.second", "first.second.second"),
        createConfigurable("first.second", "first.second.third"),
        createConfigurable("first.second", "first.second.fourth"),
        createConfigurable(null, "2nd"),
        createConfigurable("2nd", "2nd.1st"),
        createConfigurable("2nd", "2nd.2nd"),
        createConfigurable("2nd.2nd", "2nd.2nd.1st"),
        createConfigurable("2nd", "2nd.3rd")
      ),
      null,
      ContainerUtil.newArrayList(node("first",
                                      node("first.first",
                                           node("first.first.first"),
                                           node("first.first.second",
                                                node("first.first.second.first"),
                                                node("first.first.second.second")
                                           ),
                                           node("first.first.third"),
                                           node("first.first.fourth")
                                      ),
                                      node("first.second",
                                           node("first.second.first",
                                                node("first.second.first.first"),
                                                node("first.second.first.second"),
                                                node("first.second.first.third")
                                           ),
                                           node("first.second.second"),
                                           node("first.second.third"),
                                           node("first.second.fourth")
                                      )
                                 ),
                                 node("2nd",
                                      node("2nd.1st"),
                                      node("2nd.2nd",
                                           node("2nd.2nd.1st")
                                      ),
                                      node("2nd.3rd")
                                 )
      )
    );
  }

  public void testChildFiltering() throws Exception {
    matchStructures(
      ContainerUtil.newArrayList(
        createConfigurable(null, "parent"),
        createConfigurable("parent", "1st.child"),
        createConfigurable("1st.child", "1st.child.1"),
        createConfigurable("parent", "2nd.child"),
        createConfigurable("2nd.child", "2nd.child.1"),
        createConfigurable("parent", "3rd.child")
      ),
      new ConfigurableFilter() {
        @Override
        public boolean isIncluded(Configurable configurable) {
          String displayName = configurable.getDisplayName();
          return displayName.equals("parent") ||
                 displayName.equals("1st.child") ||
                 displayName.equals("2nd.child") ||
                 displayName.equals("2nd.child.1");
        }
      },
      ContainerUtil.newArrayList(node("parent",
                                      node("1st.child"),
                                      node("2nd.child",
                                           node("2nd.child.1")
                                      )
                                 )
      )
    );
  }

  private static void append(StringBuilder sb, String name, String value) {
    sb.append(' ').append(name).append('=').append('"').append(value).append('"');
  }

  private static Configurable wrapConfigurable(String id, String... values) throws Exception {
    StringBuilder sb = new StringBuilder("<projectConfigurable");
    append(sb, "instance", Configurable.class.getName());
    append(sb, "displayName", id);
    append(sb, "id", id);
    for (String value : values) {
      int index = value.indexOf(':');
      append(sb, value.substring(0, index), value.substring(index + 1));
    }
    return ConfigurableWrapper.wrapConfigurable(deserializeConfigurable(sb.append("/>").toString()));
  }

  private static ConfigurableEP<Configurable> createConfigurable(@Nullable String parentId, @NotNull String id) throws IOException, JDOMException {
    StringBuilder xml = new StringBuilder("<projectConfigurable");
    xml.append(" instance=\"").append(Configurable.class.getName()).append("\"");
    xml.append(" displayName=\"").append(id).append("\"");
    xml.append(" id=\"").append(id).append("\"");
    if (parentId != null) {
      xml.append(" parentId=\"").append(parentId).append("\"");
    }
    xml.append("/>");
    return deserializeConfigurable(xml.toString());
  }

  @NotNull
  private static ConfigurableEP<Configurable> deserializeConfigurable(@NotNull String text) throws IOException, JDOMException {
    Element element = JdomKt.loadElement(text);
    ConfigurableEP<Configurable> bean = new ConfigurableEP<>();
    XmlSerializer.deserializeInto(element, bean);
    return bean;
  }

  private static void matchStructures(@NotNull List<ConfigurableEP<Configurable>> configurableEPs,
                                      @Nullable ConfigurableFilter filter,
                                      @NotNull List<Node> expectedTopLevelNodes) {
    //noinspection unchecked
    ConfigurableEP<Configurable>[] extensions = configurableEPs.toArray(new ConfigurableEP[configurableEPs.size()]);
    List<Configurable> list = ConfigurableExtensionPointUtil.buildConfigurablesList(extensions, filter);
    assertEquals(expectedTopLevelNodes.size(), list.size());
    for (int i = 0; i < list.size(); i++) {
      matchNodesDeeply(list.get(i), expectedTopLevelNodes.get(i));
    }
  }

  private static void matchNodesDeeply(@NotNull Configurable configurable, @NotNull Node node) {
    if (configurable instanceof SearchableConfigurable) {
      SearchableConfigurable searchableConfigurable = (SearchableConfigurable) configurable;
      assertEquals(node.getId(), searchableConfigurable.getId());
      List<Configurable> children = Collections.emptyList();
      if (configurable instanceof Configurable.Composite) {
        Configurable.Composite composite = (Configurable.Composite) configurable;
        children = Arrays.asList(composite.getConfigurables());
      }
      assertEquals(node.getChildren().size(), children.size());
      for (int i = 0; i < children.size(); i++) {
        matchNodesDeeply(children.get(i), node.getChildren().get(i));
      }
    }
    else {
      fail("Cannot cast to " + SearchableConfigurable.class.getName() + ", " + configurable);
    }
  }

  public void testSimple() throws Exception {
    assertEquals(ContainerUtil.newArrayList(
                   node("other",
                        node("A",
                             node("B"),
                             node("C",
                                  node("D"))))),
                 build(
                   wrapConfigurable("A"),
                   wrapConfigurable("B", "parentId:A"),
                   wrapConfigurable("C", "parentId:A"),
                   wrapConfigurable("D", "parentId:C")));
  }

  public void testSimpleSort() throws Exception {
    assertEquals(ContainerUtil.newArrayList(
                   node("other",
                        node("sorted",
                             node("s3"),
                             node("s7"),
                             node("s8"),
                             node("s1"),
                             node("s2"),
                             node("s9"),
                             node("s4"),
                             node("s5"),
                             node("s6")),
                        node("unsorted",
                             node("u1"),
                             node("u9"),
                             node("u2"),
                             node("u8"),
                             node("u3"),
                             node("u7"),
                             node("u4"),
                             node("u6"),
                             node("u5")))),
                 build(
                   wrapConfigurable("sorted"),
                   wrapConfigurable("unsorted"),
                   wrapConfigurable("s1", "parentId:sorted"),
                   wrapConfigurable("s9", "parentId:sorted"),
                   wrapConfigurable("s2", "parentId:sorted"),
                   wrapConfigurable("s8", "parentId:sorted", "groupWeight:1"),
                   wrapConfigurable("s3", "parentId:sorted", "groupWeight:1"),
                   wrapConfigurable("s7", "parentId:sorted", "groupWeight:1"),
                   wrapConfigurable("s4", "parentId:sorted", "groupWeight:-1"),
                   wrapConfigurable("s6", "parentId:sorted", "groupWeight:-1"),
                   wrapConfigurable("s5", "parentId:sorted", "groupWeight:-1"),
                   wrapConfigurable("u1", "parentId:unsorted"),
                   wrapConfigurable("u9", "parentId:unsorted"),
                   wrapConfigurable("u2", "parentId:unsorted"),
                   wrapConfigurable("u8", "parentId:unsorted"),
                   wrapConfigurable("u3", "parentId:unsorted"),
                   wrapConfigurable("u7", "parentId:unsorted"),
                   wrapConfigurable("u4", "parentId:unsorted"),
                   wrapConfigurable("u6", "parentId:unsorted"),
                   wrapConfigurable("u5", "parentId:unsorted")));
  }

  public void testComplex() throws Exception {
    assertEquals(ContainerUtil.newArrayList(
                   node("other",
                        node("first",
                             node("first.first",
                                  node("first.first.first"),
                                  node("first.first.second",
                                       node("first.first.second.first"),
                                       node("first.first.second.second")
                                  ),
                                  node("first.first.third"),
                                  node("first.first.fourth")
                             ),
                             node("first.second",
                                  node("first.second.first",
                                       node("first.second.first.first"),
                                       node("first.second.first.second"),
                                       node("first.second.first.third")
                                  ),
                                  node("first.second.second"),
                                  node("first.second.third"),
                                  node("first.second.fourth")
                             )
                        ),
                        node("2nd",
                             node("2nd.1st"),
                             node("2nd.2nd",
                                  node("2nd.2nd.1st")
                             ),
                             node("2nd.3rd")
                        ))),
                 build(
                   wrapConfigurable("first"),
                   wrapConfigurable("first.first", "parentId:first"),
                   wrapConfigurable("first.first.first", "parentId:first.first"),
                   wrapConfigurable("first.first.second", "parentId:first.first"),
                   wrapConfigurable("first.first.second.first", "parentId:first.first.second"),
                   wrapConfigurable("first.first.second.second", "parentId:first.first.second"),
                   wrapConfigurable("first.first.third", "parentId:first.first"),
                   wrapConfigurable("first.first.fourth", "parentId:first.first"),
                   wrapConfigurable("first.second", "parentId:first"),
                   wrapConfigurable("first.second.first", "parentId:first.second"),
                   wrapConfigurable("first.second.first.first", "parentId:first.second.first"),
                   wrapConfigurable("first.second.first.second", "parentId:first.second.first"),
                   wrapConfigurable("first.second.first.third", "parentId:first.second.first"),
                   wrapConfigurable("first.second.second", "parentId:first.second"),
                   wrapConfigurable("first.second.third", "parentId:first.second"),
                   wrapConfigurable("first.second.fourth", "parentId:first.second"),
                   wrapConfigurable("2nd"),
                   wrapConfigurable("2nd.1st", "parentId:2nd"),
                   wrapConfigurable("2nd.2nd", "parentId:2nd"),
                   wrapConfigurable("2nd.2nd.1st", "parentId:2nd.2nd"),
                   wrapConfigurable("2nd.3rd", "parentId:2nd")));
  }

  public void testGroupWarning() throws Exception {
    assertEquals(ContainerUtil.newArrayList(
                   node("1",
                        node("11"),
                        node("12"),
                        node("13")),
                   node("2",
                        node("21"),
                        node("22"),
                        node("23")),
                   node("3",
                        node("31"),
                        node("32"),
                        node("33"))),
                 build(
                   wrapConfigurable("11", "parentId:1"),
                   wrapConfigurable("12", "parentId:1", "groupId:X"),
                   wrapConfigurable("13", "groupId:1"),
                   wrapConfigurable("21", "parentId:2"),
                   wrapConfigurable("22", "parentId:2", "groupId:X"),
                   wrapConfigurable("23", "groupId:2"),
                   wrapConfigurable("31", "parentId:3"),
                   wrapConfigurable("32", "parentId:3", "groupId:X"),
                   wrapConfigurable("33", "groupId:3")));
  }

  public void testCyclicWarning() throws Exception {
    assertEquals(ContainerUtil.newArrayList(
                   node("other",
                        node("0"),
                        node("4",
                             node("3",
                                  node("2",
                                       node("1")))))),
                 build(
                   wrapConfigurable("0", "parentId:0"),
                   wrapConfigurable("1", "parentId:2"),
                   wrapConfigurable("2", "parentId:3"),
                   wrapConfigurable("3", "parentId:4"),
                   wrapConfigurable("4", "parentId:1")));
  }

  private static Node getRootGroup(boolean optimize) throws Exception {
    return node(Arrays.asList(
      wrapConfigurable("root.center", "parentId:root"),
      wrapConfigurable("root.lower", "parentId:root", "groupWeight:-1000000"),
      wrapConfigurable("root.upper", "parentId:root", "groupWeight:1000000"),
      wrapConfigurable("appearance.1", "parentId:appearance", "groupWeight:11"),
      wrapConfigurable("appearance.2", "parentId:appearance", "groupWeight:1"),
      wrapConfigurable("appearance.3", "parentId:appearance"),
      wrapConfigurable("appearance.4", "parentId:appearance"),
      wrapConfigurable("editor.4", "parentId:editor", "groupWeight:-11"),
      wrapConfigurable("editor.3", "parentId:editor", "groupWeight:-1"),
      wrapConfigurable("editor.2", "parentId:editor"),
      wrapConfigurable("editor.1", "parentId:editor"),
      wrapConfigurable("build.tools.raven", "parentId:build.tools"),
      wrapConfigurable("build.tools.maven", "parentId:build.tools"),
      wrapConfigurable("build.center", "parentId:build"),
      wrapConfigurable("build.upper", "parentId:build", "groupWeight:1000000"),
      wrapConfigurable("build.lower", "parentId:build", "groupWeight:-1000000"),
      wrapConfigurable("tools.lower", "parentId:tools", "groupWeight:-1000000"),
      wrapConfigurable("tools.upper", "parentId:tools", "groupWeight:1000000"),
      wrapConfigurable("tools.center", "parentId:tools"),
      wrapConfigurable("external.plugin"),
      wrapConfigurable("other.settings")
    ), optimize);
  }

  public void testRootGroup() throws Exception {
    assertEquals(node("configurable.group.root",
                      node("root.upper"),
                      node("configurable.group.appearance",
                           node("appearance.1"),
                           node("appearance.2"),
                           node("appearance.3"),
                           node("appearance.4")),
                      node("configurable.group.editor",
                           node("editor.1"),
                           node("editor.2"),
                           node("editor.3"),
                           node("editor.4")),
                      node("configurable.group.build",
                           node("build.upper"),
                           node("configurable.group.build.tools",
                                node("build.tools.maven"),
                                node("build.tools.raven")),
                           node("build.center"),
                           node("build.lower")),
                      node("configurable.group.tools",
                           node("tools.upper"),
                           node("tools.center"),
                           node("tools.lower")),
                      node("root.center"),
                      node("configurable.group.other",
                           node("external.plugin"),
                           node("other.settings")),
                      node("root.lower")),
                 getRootGroup(false));
  }

  private static Node getRootCustom(boolean optimize) throws Exception {
    String bundle = "bundle:" + ConfigurableExtensionPointUtilTest.class.getName();
    return node(Arrays.asList(
      wrapConfigurable("custom.configurable", bundle, "parentId:custom"),
      wrapConfigurable("missed.configurable", bundle, "parentId:missed"),
      wrapConfigurable("cyclic.configurable", bundle, "parentId:cyclic"),
      wrapConfigurable("cyclic.chain", bundle, "parentId:cycle5")
    ), optimize);
  }

  public void testRootCustom() throws Exception {
    assertEquals(node("configurable.group.root",
                      node("configurable.group.cycle1",
                           node("configurable.group.cycle2",
                                node("configurable.group.cycle3",
                                     node("configurable.group.cycle4",
                                          node("configurable.group.cycle5",
                                               node("cyclic.chain")))))),
                      node("configurable.group.cyclic",
                           node("cyclic.configurable")),
                      node("configurable.group.parent",
                           node("configurable.group.custom",
                                node("custom.configurable"))),
                      node("configurable.group.other",
                           node("missed.configurable"))),
                 getRootCustom(false));
  }

  public void testRootCustomOptimized() throws Exception {
    assertEquals(node("configurable.group.root",
                      node("custom.configurable"),
                      node("cyclic.chain"),
                      node("cyclic.configurable"),
                      node("missed.configurable")),
                 getRootCustom(true));
  }

  private static Node getRootSimple(boolean optimize) throws Exception {
    return node(Arrays.asList(
      wrapConfigurable("editor.configurable", "parentId:editor"),
      wrapConfigurable("another.configurable")
    ), optimize);
  }

  public void testRootSimple() throws Exception {
    assertEquals(node("configurable.group.root",
                      node("configurable.group.editor",
                           node("editor.configurable")),
                      node("configurable.group.other",
                           node("another.configurable"))),
                 getRootSimple(false));
  }

  public void testRootSimpleOptimizedSorting() throws Exception {
    // ensure that configurables sorted according their groups
    assertEquals(node("configurable.group.root",
                      node("editor.configurable"),
                      node("another.configurable")),
                 getRootSimple(true));
  }

  private static List<Node> build(Configurable... configurables) {
    Map<String, List<Configurable>> map = ConfigurableExtensionPointUtil.groupConfigurables(Arrays.asList(configurables));
    List<Node> children = ContainerUtil.newArrayList();
    for (Map.Entry<String, List<Configurable>> entry : ContainerUtil.newTreeMap(map).entrySet()) {
      children.add(node(entry.getKey(), entry.getValue()));
    }
    return children;
  }

  private static Node node(List<Configurable> configurables, boolean optimize) {
    Registry.get("ide.settings.replace.group.with.single.configurable").setValue(optimize);
    return node((Configurable)ConfigurableExtensionPointUtil.getConfigurableGroup(configurables, null));
  }

  private static Node node(Configurable configurable) {
    @SuppressWarnings("unchecked")
    SearchableConfigurable sc = (SearchableConfigurable)configurable;
    if (configurable instanceof Configurable.Composite) {
      Configurable.Composite composite = (Configurable.Composite)configurable;
      return node(sc.getId(), Arrays.asList(composite.getConfigurables()));
    }
    return node(sc.getId());
  }

  @NotNull
  private static Node node(String id, Node... children) {
    return new Node(id, Arrays.asList(children));
  }

  private static Node node(String id, List<Configurable> configurables) {
    List<Node> children = ContainerUtil.newArrayList();
    for (Configurable configurable : configurables) {
      children.add(node(configurable));
    }
    return new Node(id, children);
  }

  private static class Node {
    private final String myId;
    private final List<Node> myChildren;

    private Node(String id, List<Node> children) {
      myId = id;
      myChildren = children;
    }

    public String getId() {
      return myId;
    }

    @NotNull
    public List<Node> getChildren() {
      return myChildren;
    }

    @Override
    public boolean equals(Object object) {
      if (object instanceof Node) {
        Node node = (Node)object;
        if (node.myId == null ? myId == null : node.myId.equals(myId)) {
          return node.myChildren.equals(myChildren);
        }
      }
      return false;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder(myId);
      int count = myChildren.size();
      if (count > 0) {
        sb.append(" with ").append(count).append(" child");
        if (count > 1) sb.append("ren");
      }
      return sb.toString();
    }
  }
}
