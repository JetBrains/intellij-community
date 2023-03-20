// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.components;

import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.internal.inspector.PropertyBean;
import com.intellij.internal.inspector.UiInspectorAction;
import com.intellij.internal.inspector.UiInspectorUtil;
import com.intellij.openapi.ui.DialogPanel;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.PlatformColors;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.List;
import java.util.*;

@ApiStatus.Internal
public abstract class HierarchyTree extends JTree implements TreeSelectionListener {
  private static final int MAX_DEEPNESS_TO_DISCOVER_FIELD_NAME = 8;

  final Component myComponent;

  HierarchyTree(Component c) {
    myComponent = c;
    setModel(buildModel(c));
    setCellRenderer(new ComponentTreeCellRenderer(c));
    getSelectionModel().addTreeSelectionListener(this);
    TreeUIHelper.getInstance().installTreeSpeedSearch(this);
    if (c instanceof JComponent && ClientProperty.get(c, UiInspectorAction.CLICK_INFO) != null) {
      SwingUtilities.invokeLater(() -> getSelectionModel().setSelectionPath(getPathForRow(getLeadSelectionRow() + 1)));
    }
  }

  private static TreeModel buildModel(Component c) {
    return buildModel(c, false);
  }

  private static TreeModel buildModel(Component c, boolean accessibleModel) {
    Component parent = null;
    if (accessibleModel && (c instanceof Accessible)) {
      Accessible axComponent = c.getAccessibleContext().getAccessibleParent();
      if (axComponent instanceof Component) {
        parent = ((Component)axComponent);
      }
    }
    else {
      parent = c.getParent();
    }
    while (parent != null) {
      c = parent;
      //Find root window
      if (accessibleModel && (c instanceof Accessible)) {
        Accessible axComponent = c.getAccessibleContext().getAccessibleParent();
        if (axComponent instanceof Component) {
          parent = ((Component)axComponent);
        }
        else {
          parent = null;
        }
      }
      else {
        parent = c.getParent();
      }
    }
    return new DefaultTreeModel(ComponentNode.createComponentNode(c, accessibleModel));
  }

  public void resetModel(Component c, boolean accessibleModel) {
    setModel(buildModel(c, accessibleModel));
  }

  @Override
  public String convertValueToText(Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if (value instanceof ComponentNode) {
      Pair<Class<?>, String> pair = null;
      if (((ComponentNode)value).myComponent != null) {
        pair = getClassAndFieldName(((ComponentNode)value).myComponent);
      }
      if (pair != null) {
        return pair.first.getSimpleName() + '.' + pair.second;
      }
      else {
        return myComponent.getClass().getName();
      }
    }
    return super.convertValueToText(value, selected, expanded, leaf, row, hasFocus);//todo
  }

  public void expandPath() {
    expandPath(false);
  }

  public void expandPath(boolean isAccessibleTree) {
    TreeUtil.expandAll(this);
    int count = getRowCount();
    ComponentNode node = ComponentNode.createComponentNode(myComponent, isAccessibleTree);

    for (int i = 0; i < count; i++) {
      TreePath row = getPathForRow(i);
      if (row.getLastPathComponent().equals(node)) {
        setSelectionPath(row);
        scrollPathToVisible(getSelectionPath());
        break;
      }
    }
  }

  @Override
  public void valueChanged(TreeSelectionEvent e) {
    TreePath[] paths = getSelectionPaths();
    if (paths == null) {
      onComponentsChanged(Collections.emptyList());
      return;
    }

    List<List<PropertyBean>> clickInfos = ContainerUtil.mapNotNull(paths, path -> {
      Object node = path.getLastPathComponent();
      if (node instanceof ComponentNode) {
        if (((ComponentNode)node).getUserObject() instanceof List<?>) {
          //it's renderer and we present it as ComponentNode instead of outdated ClickInfoNode
          //noinspection unchecked
          return (List<PropertyBean>)((ComponentNode)node).getUserObject();
        }
      }
      //if (node instanceof ClickInfoNode) return ((ClickInfoNode)node).getInfo();
      return null;
    });
    if (!clickInfos.isEmpty()) {
      onClickInfoChanged(clickInfos.get(0));
      return;
    }

    List<Component> components = ContainerUtil.mapNotNull(paths, path -> {
      Object node = path.getLastPathComponent();
      if (node instanceof ComponentNode) return ((ComponentNode)node).getComponent();
      return null;
    });
    onComponentsChanged(components);
  }

  public abstract void onClickInfoChanged(List<? extends PropertyBean> info);

  public abstract void onComponentsChanged(List<? extends Component> components);

  public static final class ComponentNode extends DefaultMutableTreeNode {
    private final Component myComponent;
    private final Accessible myAccessible;
    private final String myName;
    private final boolean isAccessibleNode;

    String myText;

    public static ComponentNode createAccessibleNode(@NotNull Accessible accessible) {
      String name = accessible.getClass().getName();
      JComponent component = ObjectUtils.tryCast(accessible, JComponent.class);
      ComponentNode node = new ComponentNode(component, accessible, name, true);
      TreeUtil.addChildrenTo(node, prepareAccessibleChildren(accessible));
      return node;
    }

    public static ComponentNode createComponentNode(@NotNull Component component) {
      return createComponentNode(component, false);
    }

    public static ComponentNode createComponentNode(@NotNull Component component, boolean isAccessibleComponent) {
      String name = component.getClass().getName();
      Accessible accessible = ObjectUtils.tryCast(component, Accessible.class);
      ComponentNode node = new ComponentNode(component, accessible, name, isAccessibleComponent);
      if (isAccessibleComponent) {
        TreeUtil.addChildrenTo(node, prepareAccessibleChildren(accessible));
      }
      else {
        TreeUtil.addChildrenTo(node, prepareComponentChildren(component));
      }
      return node;
    }

    public static ComponentNode createNamedNode(@NotNull String name, @Nullable Component component) {
      Accessible accessible = ObjectUtils.tryCast(component, Accessible.class);
      ComponentNode node = new ComponentNode(component, accessible, name, false);
      TreeUtil.addChildrenTo(node, prepareComponentChildren(component));
      return node;
    }

    private ComponentNode(@Nullable Component component,
                          @Nullable Accessible accessible,
                          @NotNull String name,
                          boolean isAccessibleComponent) {
      super(component);
      myComponent = component;
      myAccessible = accessible;
      myName = name;
      isAccessibleNode = isAccessibleComponent;
    }

    private static List<TreeNode> prepareAccessibleChildren(@Nullable Accessible a) {
      List<TreeNode> result = new ArrayList<>();
      if (a != null) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac != null) {
          int count = ac.getAccessibleChildrenCount();
          for (int i = 0; i < count; i++) {
            Accessible axComponent = a.getAccessibleContext().getAccessibleChild(i);
            result.add(createAccessibleNode(axComponent));
          }
        }
      }
      return result;
    }

    public Component getComponent() {
      return myComponent;
    }

    public Accessible getAccessible() {
      return myAccessible;
    }

    @Override
    public String toString() {
      if (myText != null) return myText;
      return myName;
    }

    private void setText(String value) {
      myText = value;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ComponentNode && ((ComponentNode)obj).getComponent() == getComponent();
    }

    private static List<TreeNode> prepareComponentChildren(Component parent) {
      List<TreeNode> result = new ArrayList<>();

      if (parent instanceof JComponent) {
        DefaultMutableTreeNode node = ClientProperty.get(parent, UiInspectorAction.CLICK_INFO);
        if (node != null) {
          result.add(node);
        }
      }
      if (parent instanceof Container) {
        for (Component component : ((Container)parent).getComponents()) {
          result.add(createComponentNode(component));
        }
      }
      if (parent instanceof Window) {
        Window[] children = ((Window)parent).getOwnedWindows();
        for (Window child : children) {
          if (child instanceof InspectorWindow) continue;
          result.add(createComponentNode(child));
        }
      }

      return result;
    }
  }

  private static class ComponentTreeCellRenderer extends ColoredTreeCellRenderer {
    private final Component myInitialSelection;

    ComponentTreeCellRenderer(Component initialSelection) {
      myInitialSelection = initialSelection;
      setFont(JBUI.Fonts.label(11));
      setBorder(JBUI.Borders.empty(0, 3));
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      Color foreground = UIUtil.getTreeForeground(selected, hasFocus);
      Color background = selected ? UIUtil.getTreeSelectionBackground(hasFocus) : null;
      boolean isRenderer = false;
      if (value instanceof ComponentNode componentNode) {
        isRenderer = componentNode.getUserObject() instanceof List<?>;
        Component component = componentNode.getComponent();

        if (component != null && !selected) {
          if (!component.isVisible()) {
            foreground = JBColor.GRAY;
          }
          else if (component.getWidth() == 0 || component.getHeight() == 0) {
            foreground = new JBColor(new Color(128, 10, 0), JBColor.BLUE);
          }
          else if (component.getPreferredSize() != null &&
                   (component.getSize().width < component.getPreferredSize().width
                    || component.getSize().height < component.getPreferredSize().height)) {
            foreground = PlatformColors.BLUE;
          }

          if (myInitialSelection == componentNode.getComponent()) {
            //noinspection UseJBColor
            background = new Color(31, 128, 8, 58);
          }
        }

        if (componentNode.isAccessibleNode) {
          AccessibleContext ac;
          if (component != null) {
            ac = component.getAccessibleContext();
          }
          else {
            ac = componentNode.getAccessible().getAccessibleContext();
          }
          String simpleName = ac.getClass().getSimpleName();
          if (StringUtil.isEmpty(simpleName)) {
            append(ac.getClass().getName());
          }
          else {
            append(simpleName);
          }
          String axName = ac.getAccessibleName();
          if (axName != null) {
            append(" " + axName);
          }
        }
        else if (component != null) {
          append(UiInspectorUtil.getComponentName(component));
          Pair<Class<?>, String> class2field = getClassAndFieldName(component);
          if (class2field != null) {
            append("(" + class2field.second + "@" + class2field.first.getSimpleName() + ")");
          }

          append(": " + ValueCellRenderer.RectangleRenderer.toString(component.getBounds()), SimpleTextAttributes.GRAYED_ATTRIBUTES);
          if (component.isOpaque()) {
            append(", opaque", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
          if (component.isDoubleBuffered()) {
            append(", double-buffered", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
          if (DataManagerImpl.getDataProviderEx(component) != null) {
            append(", ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            append("data-provider", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          }
          componentNode.setText(toString());
          setIcon(Icons.findIconFor(component));
        }
        else {
          append(componentNode.myName);
        }
      }
      if (isRenderer) {
        setIcon(AllIcons.Ide.Rating);
      }
      setForeground(foreground);
      setBackground(background);

      SpeedSearchUtil.applySpeedSearchHighlighting(tree, this, false, selected);
    }
  }

  static final class Icons {
    private static final Map<Class<?>, Icon> COMPONENT_MAPPING = new HashMap<>();

    private static @NotNull Icon load(@NotNull String path) {
      return load(path, null);
    }

    private static @NotNull Icon load(@NotNull String path, Class<?> cls) {
      Icon icon = IconLoader.getIcon("com/intellij/internal/inspector/icons/" + path, UiInspectorAction.class.getClassLoader());
      if (cls != null) {
        COMPONENT_MAPPING.put(cls, icon);
      }
      return icon;
    }

    static {
      load("button.svg", JButton.class);
      load("checkBox.svg", JCheckBox.class);
      load("comboBox.svg", JComboBox.class);
      load("editorPane.svg", JEditorPane.class);
      load("formattedTextField.svg", JFormattedTextField.class);
      load("label.svg", JLabel.class);
      load("list.svg", JList.class);
      load("panel.svg", JPanel.class);
      load("passwordField.svg", JPasswordField.class);
      load("progressbar.svg", JProgressBar.class);
      load("radioButton.svg", JRadioButton.class);
      load("scrollbar.svg", JScrollBar.class);
      load("scrollPane.svg", JScrollPane.class);
      load("separator.svg", JSeparator.class);
      load("slider.svg", JSlider.class);
      load("spinner.svg", JSpinner.class);
      load("splitPane.svg", JSplitPane.class);
      load("tabbedPane.svg", JTabbedPane.class);
      load("table.svg", JTable.class);
      load("textArea.svg", JTextArea.class);
      load("textField.svg", JTextField.class);
      load("textPane.svg", JTextPane.class);
      load("toolbar.svg", JToolBar.class);
      //load("toolbarSeparator.svg");
      load("tree.svg", JTree.class);
    }

    static final @NotNull Icon Kotlin = load("kotlin.svg");
    static final @NotNull Icon Unknown = load("unknown.svg");

    public static Icon findIconFor(Component component) {
      Class<?> aClass = component.getClass();
      Icon icon = null;
      while (icon == null && aClass != null) {
        icon = COMPONENT_MAPPING.get(aClass);
        aClass = aClass.getSuperclass();
      }
      if (icon == null) icon = Unknown;

      if (ComponentUtil.findParentByCondition(component, (c) -> c.getClass() == DialogPanel.class) != null) {
        Icon kotlinIcon = ((ScalableIcon)Kotlin).scale(0.5f);
        return new RowIcon(icon, IconUtil.toSize(kotlinIcon, icon.getIconWidth(), icon.getIconHeight()));
      }
      return icon;
    }
  }

  @Nullable
  private static Pair<Class<?>, String> getClassAndFieldName(Component component) {
    Container parent = component.getParent();
    int deepness = 1;
    while (parent != null && deepness <= MAX_DEEPNESS_TO_DISCOVER_FIELD_NAME) {
      Class<?> aClass = parent.getClass();
      Map<Field, Class<?>> fields = new HashMap<>();
      while (aClass != null) {
        for (Field field : aClass.getDeclaredFields()) {
          fields.put(field, aClass);
        }
        aClass = aClass.getSuperclass();
      }
      for (Map.Entry<Field, Class<?>> entry : fields.entrySet()) {
        try {
          Field field = entry.getKey();
          field.setAccessible(true);
          if (field.get(parent) == component) {
            return Pair.create(entry.getValue(), field.getName());
          }
        }
        catch (IllegalAccessException | InaccessibleObjectException e) {
          //skip
        }
      }
      parent = parent.getParent();
      deepness++;
    }
    return null;
  }
}
