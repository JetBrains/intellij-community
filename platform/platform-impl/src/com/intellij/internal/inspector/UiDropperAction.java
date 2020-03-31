// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.inspector;

import com.intellij.ide.IdeBundle;
import com.intellij.internal.InternalActionsBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.StripeTable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.*;

import static java.awt.event.MouseEvent.BUTTON1;
import static java.awt.event.MouseEvent.MOUSE_CLICKED;

public class UiDropperAction extends ToggleAction implements DumbAware {


  private static AnAction clickAction;
  final private static String UI_DROPPER_PLACE = "UI Dropper Place";
  private UiDropper myUiDropper;


  public static void setClickAction(@NotNull AnAction action){
    clickAction = action;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return myUiDropper != null;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    if (state) {
      if (myUiDropper == null) {
        myUiDropper = new UiDropper();
      }

      UiInspectorNotification[] existing =
        NotificationsManager.getNotificationsManager().getNotificationsOfType(UiInspectorNotification.class, null);
      if (existing.length == 0) {
        Notifications.Bus.notify(new UiInspectorNotification(), null);
      }
    }
    else {
      UiDropper inspector = myUiDropper;
      myUiDropper = null;
      if (inspector != null) {
        Disposer.dispose(inspector);
      }
    }
  }

  private static class UiInspectorNotification extends Notification {
    private UiInspectorNotification() {
      super(Notifications.SYSTEM_MESSAGES_GROUP_ID, "UI Dropper", "Hold ctr + alt and navigate cursor to component.\nUse mouse wheel to get info of parent component.",
            NotificationType.INFORMATION);
    }
  }

  private static class InspectorWindow extends JDialog {
    private InspectorTable myInspectorTable;
    private Component myComponent;
    private HighlightComponent myHighlightComponent;
    private final HierarchyTree myHierarchyTree;
    private final JPanel myWrapperPanel;

    private InspectorWindow(@NotNull Component component) throws HeadlessException {
      super(findWindow(component));
      Window window = findWindow(component);
      setModal(window instanceof JDialog && ((JDialog)window).isModal());
      myComponent = component;
      getRootPane().setBorder(JBUI.Borders.empty(5));

      setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

      setLayout(new BorderLayout());
      setTitle(component.getClass().getName());

      DefaultActionGroup actions = new DefaultActionGroup();
      actions.addAction(new IconWithTextAction(IdeBundle.messagePointer("action.Anonymous.text.highlight")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          //highlightCmp(myHighlightComponent == null);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabled(myComponent != null && myComponent.isVisible());
        }
      });

      actions.addSeparator();

      actions.add(new IconWithTextAction(InternalActionsBundle.messagePointer("action.Anonymous.text.refresh")) {

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          getCurrentTable().refresh();
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabled(myComponent != null && myComponent.isVisible());
        }
      });

      ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.CONTEXT_TOOLBAR, actions, true);
      add(toolbar.getComponent(), BorderLayout.NORTH);

      myWrapperPanel = new JPanel(new BorderLayout());

      myInspectorTable = new InspectorTable(component);
      myHierarchyTree = new HierarchyTree(component) {
        @Override
        public void onComponentChanged(Component c) {
          boolean wasHighlighted = myHighlightComponent != null;
          //highlightCmp(false);
          switchInfo(c);
          //highlightCmp(wasHighlighted);
        }
      };

      myWrapperPanel.add(myInspectorTable, BorderLayout.CENTER);

      JSplitPane splitPane = new JSplitPane();
      splitPane.setDividerLocation(0.5);
      splitPane.setRightComponent(myWrapperPanel);

      JScrollPane pane = new JBScrollPane(myHierarchyTree);
      splitPane.setLeftComponent(pane);
      add(splitPane, BorderLayout.CENTER);

      myHierarchyTree.expandPath();

      addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent e) {
          close();
        }
      });

      getRootPane().getActionMap().put("CLOSE", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          close();
        }
      });
      //highlightCmp(true);
      getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "CLOSE");
    }

    private static Window findWindow(Component component) {
      DialogWrapper dialogWrapper = DialogWrapper.findInstance(component);
      if (dialogWrapper != null) {
        return dialogWrapper.getPeer().getWindow();
      }
      return null;
    }

    private InspectorTable getCurrentTable() {
      return myInspectorTable;
    }

    private void switchInfo(@Nullable Component c) {
      if (c == null) return;
      myComponent = c;
      setTitle(myComponent.getClass().getName());
      myWrapperPanel.removeAll();
      myInspectorTable = new InspectorTable(c);
      myWrapperPanel.add(myInspectorTable, BorderLayout.CENTER);
      myWrapperPanel.revalidate();
      myWrapperPanel.repaint();
    }

    public void close() {
      //highlightCmp(false);
      myComponent = null;
      setVisible(false);
      dispose();
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
      if (value instanceof HierarchyTree.ComponentNode) {
        HierarchyTree.ComponentNode componentNode = (HierarchyTree.ComponentNode)value;
        Component component = componentNode.getComponent();
        Class<?> clazz0 = component.getClass();
        Class<?> clazz = clazz0.isAnonymousClass() ? clazz0.getSuperclass() : clazz0;
        String name = component.getName();

        if (!selected) {
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
            background = new Color(31, 128, 8, 58);
          }
        }
        append(clazz.getSimpleName());
        if (StringUtil.isNotEmpty(name)) {
          append(" \"" + name + "\"");
        }
        append(": " + RectangleRenderer.toString(component.getBounds()), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        if (component.isOpaque()) {
          append(", opaque", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        if (component.isDoubleBuffered()) {
          append(", double-buffered", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        componentNode.setText(toString());
        setIcon(JBUI.scale(new ColorsIcon(11, component.getBackground(), component.getForeground())));
      }

      setForeground(foreground);
      setBackground(background);

      SpeedSearchUtil.applySpeedSearchHighlighting(tree, this, false, selected);
    }
  }

  private static TreeModel buildModel(Component c) {
    Component parent = c.getParent();
    while (parent != null) {
      c = parent;
      parent = c.getParent();//Find root window
    }
    return new DefaultTreeModel(new UiDropperAction.HierarchyTree.ComponentNode(c));
  }


  private abstract static class HierarchyTree extends JTree implements TreeSelectionListener {
    final Component myComponent;

    private HierarchyTree(Component c) {
      myComponent = c;
      setModel(buildModel(c));
      setCellRenderer(new ComponentTreeCellRenderer(c));
      getSelectionModel().addTreeSelectionListener(this);
      new TreeSpeedSearch(this);
    }

    public void expandPath() {
      TreeUtil.expandAll(this);
      int count = getRowCount();
      ComponentNode node = new ComponentNode(myComponent);

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
      TreePath path = e.getNewLeadSelectionPath();
      if (path == null) {
        onComponentChanged(null);
        return;
      }
      Object component = path.getLastPathComponent();
      if (component instanceof ComponentNode) {
        Component c = ((ComponentNode)component).getComponent();
        onComponentChanged(c);
      }
    }

    public abstract void onComponentChanged(Component c);

    private static class ComponentNode extends DefaultMutableTreeNode {
      private final Component myComponent;
      String myText;

      private ComponentNode(@NotNull Component component) {
        super(component);
        myComponent = component;
        children = prepareChildren(myComponent);
      }

      Component getComponent() {
        return myComponent;
      }

      @Override
      public String toString() {
        return myText != null ? myText : myComponent.getClass().getName();
      }

      public void setText(String value) {
        myText = value;
      }

      @Override
      public boolean equals(Object obj) {
        return obj instanceof ComponentNode && ((ComponentNode)obj).getComponent() == getComponent();
      }

      @SuppressWarnings("UseOfObsoleteCollectionType")
      private static Vector prepareChildren(Component parent) {
        Vector<ComponentNode> result = new Vector<>();
        if (parent instanceof Container) {
          for (Component component : ((Container)parent).getComponents()) {
            result.add(new ComponentNode(component));
          }
        }
        if (parent instanceof Window) {
          Window[] children = ((Window)parent).getOwnedWindows();
          for (Window child : children) {
            if (child instanceof InspectorWindow) continue;
            result.add(new ComponentNode(child));
          }
        }

        return result;
      }
    }
  }

  private static class MyLabel extends JLabel {
    private final JComponent myGlasspane;
    private final String myText;

    private MyLabel(@NotNull String text, @NotNull JComponent glasspane) {
      super(text);
      myText = text;
      myGlasspane = glasspane;
      setForeground(Gray._230);
      setFont(UIUtil.getLabelFont());
    }

    MyLabel(String text, JComponent glassPane, int width) {
      super(text);
      myText = text;
      myGlasspane = glassPane;
      setForeground(Gray._230);
      setFont(new Font(UIUtil.getLabelFont().getName(), Font.PLAIN, 6));
    }

    public void dispose() {
      if (myGlasspane != null) myGlasspane.remove(this);
    }

    @Override
    protected void paintComponent(Graphics g) {
      //paint background
      int cornerRadius = 0;
      Rectangle bounds = getBounds();
      Graphics2D g2 = (Graphics2D)g;
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(Gray._90);
      g2.fill(new RoundRectangle2D.Float(0, 0, bounds.width, bounds.height, cornerRadius, cornerRadius));
      super.paintComponent(g);
    }
  }

  private static class HighlightComponent extends JComponent {
    Color myColor;
    JComponent myGlassPane;
    Component myOriginalComponent;

    private HighlightComponent(@NotNull final Color c, @NotNull final JComponent glassPane, @NotNull final Component originalComponent) {
      myColor = c;
      myGlassPane = glassPane;
      myOriginalComponent = originalComponent;
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2d = (Graphics2D)g;

      Color oldColor = g2d.getColor();
      g2d.setColor(myColor);
      Composite old = g2d.getComposite();
      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));

      Rectangle r = getBounds();
      g2d.fillRect(0, 0, r.width, r.height);
      g2d.setColor(myColor.darker());
      g2d.drawRect(0, 0, r.width - 1, r.height - 1);

      g2d.setComposite(old);
      g2d.setColor(oldColor);
    }

    public void dispose() {
      if (myGlassPane != null) myGlassPane.remove(this);
    }
  }

  private static class InspectorTable extends JPanel {
    InspectorTableModel myModel;
    DimensionsComponent myDimensionComponent;

    private InspectorTable(@NotNull final Component component) {
      setLayout(new BorderLayout());

      myModel = new InspectorTableModel(component);
      StripeTable table = new StripeTable(myModel);
      new TableSpeedSearch(table);

      TableColumnModel columnModel = table.getColumnModel();
      TableColumn propertyColumn = columnModel.getColumn(0);
      propertyColumn.setMinWidth(JBUIScale.scale(200));
      propertyColumn.setMaxWidth(JBUIScale.scale(200));
      propertyColumn.setResizable(false);

      TableColumn valueColumn = columnModel.getColumn(1);
      valueColumn.setMinWidth(JBUIScale.scale(200));
      valueColumn.setResizable(false);
      valueColumn.setCellRenderer(new ValueCellRenderer());
      valueColumn.setCellEditor(new DefaultCellEditor(new JBTextField()) {
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
          Component comp = table.getCellRenderer(row, column).getTableCellRendererComponent(table, value, false, false, row, column);
          if (comp instanceof JLabel) {
            value = ((JLabel)comp).getText();
          }
          Component result = super.getTableCellEditorComponent(table, value, isSelected, row, column);
          ((JComponent)result).setBorder(BorderFactory.createLineBorder(JBColor.GRAY, 1));
          return result;
        }
      });

      table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

      add(new JBScrollPane(table), BorderLayout.CENTER);
      myDimensionComponent = new DimensionsComponent(component);
      add(myDimensionComponent, BorderLayout.SOUTH);
    }

    public void refresh() {
      myModel.refresh();
      myDimensionComponent.update();
      myDimensionComponent.repaint();
    }
  }

  private static class DimensionsComponent extends JComponent {
    Component myComponent;
    int myWidth;
    int myHeight;
    Border myBorder;
    Insets myInsets;

    private DimensionsComponent(@NotNull final Component component) {
      myComponent = component;
      setOpaque(true);
      setBackground(JBColor.WHITE);
      setBorder(JBUI.Borders.empty(5, 0));

      setFont(JBUI.Fonts.label(9));

      update();
    }

    public void update() {
      myWidth = myComponent.getWidth();
      myHeight = myComponent.getHeight();
      if (myComponent instanceof JComponent) {
        myBorder = ((JComponent)myComponent).getBorder();
        myInsets = ((JComponent)myComponent).getInsets();
      }
    }

    @Override
    protected void paintComponent(final Graphics g) {
      Graphics2D g2d = (Graphics2D)g;
      Rectangle bounds = getBounds();

      g2d.setColor(getBackground());
      Insets insets = getInsets();
      g2d.fillRect(insets.left, insets.top, bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom);
      g2d.setColor(getForeground());

      final String sizeString = myWidth + " x " + myHeight;

      FontMetrics fm = g2d.getFontMetrics();
      int sizeWidth = fm.stringWidth(sizeString);

      int fontHeight = fm.getHeight();

      g2d.drawString(sizeString, bounds.width / 2 - sizeWidth / 2, bounds.height / 2 + fontHeight / 2);

      g2d.setColor(JBColor.GRAY);

      int innerX = bounds.width / 2 - sizeWidth / 2 - 20;
      int innerY = bounds.height / 2 - fontHeight / 2 - 5;
      int innerWidth = sizeWidth + 40;
      int innerHeight = fontHeight + 10;

      g2d.drawRect(innerX, innerY, innerWidth, innerHeight);

      Insets borderInsets = null;
      if (myBorder != null) borderInsets = myBorder.getBorderInsets(myComponent);
      UIUtil.drawDottedRectangle(g2d, innerX - 15, innerY - 15, innerX - 15 + innerWidth + 30, innerY - 15 + innerHeight + 30);
      drawInsets(g2d, fm, "border", borderInsets, 15, fontHeight, innerX, innerY, innerWidth, innerHeight);

      g2d.drawRect(innerX - 30, innerY - 30, innerWidth + 60, innerHeight + 60);
      drawInsets(g2d, fm, "insets", myInsets, 30, fontHeight, innerX, innerY, innerWidth, innerHeight);
    }

    private static void drawInsets(Graphics2D g2d,
                                   FontMetrics fm,
                                   String name,
                                   Insets insets,
                                   int offset,
                                   int fontHeight,
                                   int innerX,
                                   int innerY,
                                   int innerWidth,
                                   int innerHeight) {
      g2d.setColor(JBColor.BLACK);
      g2d.drawString(name, innerX - offset + 5, innerY - offset + fontHeight);

      g2d.setColor(JBColor.GRAY);
      int dashWidth = fm.stringWidth("-");

      if (insets != null) {
        final String top = Integer.toString(insets.top);
        final String bottom = Integer.toString(insets.bottom);
        final String left = Integer.toString(insets.left);
        final String right = Integer.toString(insets.right);

        g2d.drawString(top, innerX - offset + ((innerWidth + offset * 2) / 2 - fm.stringWidth(top) / 2), innerY - offset + fontHeight);
        g2d.drawString(bottom, innerX - offset + ((innerWidth + offset * 2) / 2 - fm.stringWidth(bottom) / 2),
                       innerY - offset + innerHeight + offset * 2 - 8 + fontHeight / 2);
        g2d.drawString(left, innerX - offset + 7 - fm.stringWidth(left) / 2,
                       innerY - offset + (innerHeight + offset * 2) / 2 + fontHeight / 2);
        g2d.drawString(right, innerX + innerWidth + offset - 7 - fm.stringWidth(right) / 2,
                       innerY - offset + (innerHeight + offset * 2) / 2 + fontHeight / 2);
      }
      else {
        g2d.drawString("-", innerX - offset + ((innerWidth + offset * 2) / 2 - dashWidth / 2), innerY - offset + fontHeight);
        g2d.drawString("-", innerX - offset + ((innerWidth + offset * 2) / 2 - dashWidth / 2),
                       innerY - offset + innerHeight + offset * 2 - 8 + fontHeight / 2);
        g2d.drawString("-", innerX - offset + 7 - dashWidth / 2, innerY - offset + (innerHeight + offset * 2) / 2 + fontHeight / 2);
        g2d.drawString("-", innerX + innerWidth + offset - 7 - dashWidth / 2,
                       innerY - offset + (innerHeight + offset * 2) / 2 + fontHeight / 2);
      }
    }

    @Override
    public Dimension getMinimumSize() {
      return JBUI.size(120);
    }

    @Override
    public Dimension getPreferredSize() {
      return JBUI.size(150);
    }
  }

  private static class ValueCellRenderer implements TableCellRenderer {
    private static final Map<Class, Renderer> RENDERERS = new HashMap<>();

    static {
      RENDERERS.put(Point.class, new PointRenderer());
      RENDERERS.put(Dimension.class, new DimensionRenderer());
      RENDERERS.put(Insets.class, new InsetsRenderer());
      RENDERERS.put(Rectangle.class, new RectangleRenderer());
      RENDERERS.put(Color.class, new ColorRenderer());
      RENDERERS.put(Font.class, new FontRenderer());
      RENDERERS.put(Boolean.class, new BooleanRenderer());
      RENDERERS.put(Icon.class, new IconRenderer());
    }

    private static final Renderer<Object> DEFAULT_RENDERER = new ObjectRenderer();

    private static final JLabel NULL_RENDERER = new JLabel("-");

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (value == null) {
        NULL_RENDERER.setOpaque(isSelected);
        NULL_RENDERER.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
        NULL_RENDERER.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        return NULL_RENDERER;
      }

      Renderer<Object> renderer = ObjectUtils.notNull(getRenderer(value.getClass()), DEFAULT_RENDERER);

      JComponent result = renderer.setValue(value);
      result.setOpaque(isSelected);
      result.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
      result.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
      return result;
    }

    @Nullable
    private static Renderer<Object> getRenderer(Class clazz) {
      if (clazz == null) return null;

      Renderer<Object> renderer = (Renderer<Object>)RENDERERS.get(clazz);
      if (renderer != null) return renderer;

      Class[] interfaces = clazz.getInterfaces();
      for (Class aClass : interfaces) {
        renderer = getRenderer(aClass);
        if (renderer != null) {
          return renderer;
        }
      }
      clazz = clazz.getSuperclass();
      if (clazz != null) {
        return getRenderer(clazz);
      }
      return null;
    }
  }

  private interface Renderer<T> {
    JComponent setValue(@NotNull T value);
  }

  private static class PointRenderer extends JLabel implements Renderer<Point> {
    @Override
    public JComponent setValue(@NotNull final Point value) {
      setText(String.valueOf(value.x) + ':' + value.y);
      return this;
    }
  }

  private static class DimensionRenderer extends JLabel implements Renderer<Dimension> {
    @Override
    public JComponent setValue(@NotNull final Dimension value) {
      setText(value.width + "x" + value.height);
      return this;
    }
  }

  private static class InsetsRenderer extends JLabel implements Renderer<Insets> {
    @Override
    public JComponent setValue(@NotNull final Insets value) {
      setText("top: " + value.top + " left:" + value.left + " bottom:" + value.bottom + " right:" + value.right);
      return this;
    }
  }

  private static class RectangleRenderer extends JLabel implements Renderer<Rectangle> {
    @Override
    public JComponent setValue(@NotNull final Rectangle value) {
      setText(toString(value));
      return this;
    }

    @NotNull
    static String toString(@NotNull Rectangle r) {
      return r.width + "x" + r.height + " @ " + r.x + ":" + r.y;
    }
  }

  private static class ColorRenderer extends JLabel implements Renderer<Color> {
    @Override
    public JComponent setValue(@NotNull final Color value) {
      StringBuilder sb = new StringBuilder();
      sb.append(" r:").append(value.getRed());
      sb.append(" g:").append(value.getGreen());
      sb.append(" b:").append(value.getBlue());
      sb.append(" a:").append(value.getAlpha());

      sb.append(" argb:0x");
      String hex = Integer.toHexString(value.getRGB());
      for (int i = hex.length(); i < 8; i++) sb.append('0');
      sb.append(StringUtil.toUpperCase(hex));

      if (value instanceof UIResource) sb.append(" UIResource");
      setText(sb.toString());
      setIcon(JBUI.scale(new ColorIcon(13, 11, value, true)));
      return this;
    }
  }

  private static class FontRenderer extends JLabel implements Renderer<Font> {
    @Override
    public JComponent setValue(@NotNull final Font value) {
      StringBuilder sb = new StringBuilder();
      sb.append(value.getFontName()).append(" (").append(value.getFamily()).append("), ").append(value.getSize()).append("px");
      if (Font.BOLD == (Font.BOLD & value.getStyle())) sb.append(" bold");
      if (Font.ITALIC == (Font.ITALIC & value.getStyle())) sb.append(" italic");
      if (value instanceof UIResource) sb.append(" UIResource");
      setText(sb.toString());
      return this;
    }
  }

  private static class BooleanRenderer extends JLabel implements Renderer<Boolean> {
    @Override
    public JComponent setValue(@NotNull final Boolean value) {
      setText(value ? "Yes" : "No");
      return this;
    }
  }

  private static class IconRenderer extends JLabel implements Renderer<Icon> {
    @Override
    public JComponent setValue(@NotNull final Icon value) {
      setIcon(value);
      return this;
    }
  }

  private static class ObjectRenderer extends JLabel implements Renderer<Object> {
    {
      putClientProperty("html.disable", Boolean.TRUE);
    }

    @Override
    public JComponent setValue(@NotNull final Object value) {
      setText(String.valueOf(value).replace('\n', ' '));
      return this;
    }
  }

  private static class PropertyBean {
    final String propertyName;
    final Object propertyValue;

    PropertyBean(String name, Object value) {
      propertyName = name;
      propertyValue = value;
    }
  }

  private static class InspectorTableModel extends AbstractTableModel {

    final List<String> PROPERTIES = Arrays.asList(
      "ui", "getLocation", "getLocationOnScreen",
      "getSize", "isOpaque", "getBorder",
      "getForeground", "getBackground", "getFont",
      "getMinimumSize", "getMaximumSize", "getPreferredSize",
      "isForegroundSet", "isBackgroundSet", "isFontSet",
      "isMinimumSizeSet", "isMaximumSizeSet", "isPreferredSizeSet",
      "getText", "isEditable", "getIcon",
      "getVisibleRect", "getLayout",
      "getAlignmentX", "getAlignmentY",
      "getTooltipText", "getToolTipText",
      "isShowing", "isEnabled", "isVisible", "isDoubleBuffered",
      "isFocusable", "isFocusCycleRoot", "isFocusOwner",
      "isValid", "isDisplayable", "isLightweight"
    );

    final List<String> ACCESSIBLE_CONTEXT_PROPERTIES = Arrays.asList(
      "getAccessibleRole", "getAccessibleName", "getAccessibleDescription",
      "getAccessibleAction", "getAccessibleChildrenCount",
      "getAccessibleIndexInParent", "getAccessibleRelationSet",
      "getAccessibleStateSet", "getAccessibleEditableText",
      "getAccessibleTable", "getAccessibleText",
      "getAccessibleValue", "accessibleChangeSupport"
    );

    final Component myComponent;
    final List<PropertyBean> myProperties = new ArrayList<>();

    InspectorTableModel(@NotNull Component c) {
      myComponent = c;

      fillTable();
    }

    void fillTable() {
      addProperties("", myComponent, PROPERTIES);
      Object addedAt = myComponent instanceof JComponent ? ((JComponent)myComponent).getClientProperty("uiInspector.addedAt") : null;
      myProperties.add(new PropertyBean("added-at", addedAt));

      // Add properties related to Accessibility support. This is useful for manually
      // inspecting what kind (if any) of accessibility support components expose.
      boolean isAccessible = myComponent instanceof Accessible;
      myProperties.add(new PropertyBean("accessible", isAccessible));
      AccessibleContext context = myComponent.getAccessibleContext();
      myProperties.add(new PropertyBean("accessibleContext", context));
      if (isAccessible) {
        addProperties("  ", myComponent.getAccessibleContext(), ACCESSIBLE_CONTEXT_PROPERTIES);
      }
    }

    private void addProperties(@NotNull String prefix, @NotNull Object component, @NotNull List<String> methodNames) {
      Class<?> clazz0 = component.getClass();
      Class<?> clazz = clazz0.isAnonymousClass() ? clazz0.getSuperclass() : clazz0;
      myProperties.add(new PropertyBean(prefix + "class", clazz.getName()));
      for (String name : methodNames) {
        String propertyName = ObjectUtils.notNull(StringUtil.getPropertyName(name), name);
        Object propertyValue;
        try {
          try {
            //noinspection ConstantConditions
            propertyValue = ReflectionUtil.findMethod(Arrays.asList(clazz.getMethods()), name).invoke(component);
          }
          catch (Exception e) {
            propertyValue = ReflectionUtil.findField(clazz, null, name).get(component);
          }
          myProperties.add(new PropertyBean(prefix + propertyName, propertyValue));
        }
        catch (Exception ignored) {
        }
      }
    }

    @Override
    @Nullable
    public Object getValueAt(int row, int column) {
      final PropertyBean bean = myProperties.get(row);
      if (bean != null) {
        return column == 0 ? bean.propertyName : bean.propertyValue;
      }

      return null;
    }

    @Override
    public boolean isCellEditable(int row, int col) {
      return col == 1 && updater(myProperties.get(row)) != null;
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
      PropertyBean bean = myProperties.get(row);
      try {
        myProperties.set(row, new PropertyBean(bean.propertyName, Objects.requireNonNull(updater(bean)).fun(value)));
      }
      catch (Exception ignored) {
      }
    }

    @Nullable
    public Function<Object, Object> updater(PropertyBean bean) {
      String name = bean.propertyName.trim();
      try {
        try {
          Method getter;
          try {
            getter = myComponent.getClass().getMethod("get" + StringUtil.capitalize(name));
          }
          catch (Exception e) {
            getter = myComponent.getClass().getMethod("is" + StringUtil.capitalize(name));
          }
          final Method finalGetter = getter;
          final Method setter = myComponent.getClass().getMethod("set" + StringUtil.capitalize(name), getter.getReturnType());
          setter.setAccessible(true);
          return o -> {
            try {
              setter.invoke(myComponent, fromObject(o, finalGetter.getReturnType()));
              return finalGetter.invoke(myComponent);
            }
            catch (Exception e) {
              throw new RuntimeException(e);
            }
          };
        }
        catch (Exception e) {
          final Field field = ReflectionUtil.findField(myComponent.getClass(), null, name);
          if (Modifier.isFinal(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
            return null;
          }
          return o -> {
            try {
              field.set(myComponent, fromObject(o, field.getType()));
              return field.get(myComponent);
            }
            catch (Exception e1) {
              throw new RuntimeException(e1);
            }
          };
        }
      }
      catch (Exception ignored) {
      }
      return null;
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public int getRowCount() {
      return myProperties.size();
    }

    @Override
    public String getColumnName(int columnIndex) {
      return columnIndex == 0 ? "Property" : "Value";
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return columnIndex == 0 ? String.class : Object.class;
    }

    public void refresh() {
      myProperties.clear();
      fillTable();
      fireTableDataChanged();
    }
  }

  private static class UiDropper implements AWTEventListener, Disposable {
    Map<Component, InspectorWindow> myComponentToInspector = ContainerUtil.createWeakKeyWeakValueMap();
    HighlightComponent myHighlightComponent;
    Component lastComponent;
    MyLabel myLabel;

    UiDropper() {
      Toolkit.getDefaultToolkit()
        .addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK | AWTEvent.CONTAINER_EVENT_MASK);
      List<UiDropperActionExtension> extensions = UiDropperActionExtension.EP_NAME.getExtensionList();
      if (extensions.size() > 0) {
        AnAction action = extensions.get(0).getAnAction();
        setClickAction(action);
        return;
      }
    }

    @Override
    public void dispose() {
      Toolkit.getDefaultToolkit().removeAWTEventListener(this);
      if (myHighlightComponent != null) {
        myHighlightComponent.dispose();
        myHighlightComponent = null;
      }
      if (myLabel != null) {
        myLabel.dispose();
        myLabel = null;
      }
      myComponentToInspector.clear();
    }

    private void highlightJBListCell(JBList jbList, Point pointOnList){
      cleanupHighlighting();

      int index = jbList.locationToIndex(pointOnList);
      Rectangle cellBounds = jbList.getCellBounds(index, index);
      if (cellBounds.contains(pointOnList)) {
        Object elementAt = jbList.getModel().getElementAt(index);

        JRootPane rootPane = SwingUtilities.getRootPane(jbList);
        JComponent glassPane = (JComponent)rootPane.getGlassPane();
        if (!(glassPane instanceof IdeGlassPane)) rootPane.setGlassPane(new IdeGlassPaneImpl(rootPane));

        myHighlightComponent = new HighlightComponent(new JBColor(JBColor.GREEN, JBColor.RED), glassPane, jbList);
        if (elementAt instanceof PopupFactoryImpl.ActionItem) {
          myLabel = new MyLabel(elementAt.getClass().getName() + " (\"" + elementAt.toString() +  "\")", glassPane, cellBounds.width);
        } else {
          myLabel = new MyLabel("JBList Cell: " + elementAt.getClass().getName() + " (\"" + elementAt.toString() +  "\")", glassPane);
        }


        Point jbListLocationOnRootPane = SwingUtilities.convertPoint(jbList, new Point(0, 0), jbList.getRootPane());
        myHighlightComponent.setBounds(jbListLocationOnRootPane.x + cellBounds.x, jbListLocationOnRootPane.y + cellBounds.y, cellBounds.width, cellBounds.height);
        calcMyLabelLocation(myHighlightComponent.getBounds().getLocation(), glassPane);
        glassPane.add(myHighlightComponent);
        glassPane.add(myLabel);
        glassPane.revalidate();
        glassPane.repaint();
      } else {
        highlightCmp(true, jbList);
      }
    }

    private void highlightCmp(boolean enable, Component myComponent) {
      if (myComponent instanceof HighlightComponent) return;

      Component target = enable ? myComponent : myHighlightComponent;
      JRootPane rootPane = target == null ? null : SwingUtilities.getRootPane(target);
      JComponent glassPane = rootPane == null ? null : (JComponent)rootPane.getGlassPane();

      cleanupHighlighting();

      if (glassPane == null) {
        return;
      }
      if (enable) {
        myHighlightComponent = new HighlightComponent(new JBColor(JBColor.GREEN, JBColor.RED), glassPane, myComponent);
        myLabel = new MyLabel(myComponent.getClass().getName(), glassPane);

        Point pt = SwingUtilities.convertPoint(myComponent, new Point(0, 0), rootPane);
        myHighlightComponent.setBounds(pt.x, pt.y, myComponent.getWidth(), myComponent.getHeight());
        calcMyLabelLocation(pt, glassPane);
        glassPane.add(myHighlightComponent);
        glassPane.add(myLabel);
      }
      else {
        glassPane.remove(myHighlightComponent);
        myHighlightComponent = null;
      }
      glassPane.revalidate();
      glassPane.repaint();
    }

    @Nullable
    private static String getCellText(@NotNull JBList jbList, @NotNull Point pointOnList){
      int index = jbList.locationToIndex(pointOnList);
      Rectangle cellBounds = jbList.getCellBounds(index, index);
      if (cellBounds.contains(pointOnList)) {
        Object elementAt = jbList.getModel().getElementAt(index);
        if (elementAt instanceof PopupFactoryImpl.ActionItem) {
          return ((PopupFactoryImpl.ActionItem)elementAt).getText();
        }
        else {
          return elementAt.toString();
        }
      }
      return null;
    }

    private void cleanupHighlighting() {
      if (myHighlightComponent != null) {
        myHighlightComponent.dispose();
        myHighlightComponent = null;
      }

      if (myLabel != null) {
        myLabel.dispose();
        myLabel = null;
      }
    }

    private void calcMyLabelLocation(Point pt, JComponent glasspane) {
      int width = myLabel.getPreferredSize().width;
      int height = myLabel.getPreferredSize().height;

      int shift = 18;

      int x = pt.x;
      int y = pt.y - shift;

      if (pt.x + width > glasspane.getWidth())
        x = Math.max(0, glasspane.getWidth() - width);

      if (pt.y + height > glasspane.getHeight())
        y = Math.max(0, glasspane.getHeight() - height);

      myLabel.setBounds(x, Math.max(y, 0), width, height);
    }


    public void showInspector(@NotNull Component c) {
      InspectorWindow window = myComponentToInspector.get(c);
      if (window != null) {
        window.myHierarchyTree.setModel(buildModel(c));
        window.myHierarchyTree.setCellRenderer(new ComponentTreeCellRenderer(c));
        window.myHierarchyTree.expandPath();

        window.switchInfo(c);
        //window.highlightCmp(true);
      }
      else {
        window = new InspectorWindow(c);
        myComponentToInspector.put(c, window);
        window.pack();
      }
      window.setVisible(true);
      window.toFront();
    }

    @Override
    public void eventDispatched(AWTEvent event) {
      if (event instanceof MouseEvent) {
        processMouseEvent((MouseEvent)event);
      }
      else if (event instanceof ContainerEvent) {
        processContainerEvent((ContainerEvent)event);
      }
    }

    private void processMouseEvent(MouseEvent me) {
      if (!me.isAltDown() || !me.isControlDown()) {
        cleanupHighlighting();
        return;
      }


      //increase highlighting component range
      if (me instanceof MouseWheelEvent) {
        if (((MouseWheelEvent)me).getWheelRotation() > 0 && lastComponent != null && lastComponent.getParent() != null) {
          lastComponent = lastComponent.getParent();
          highlightCmp(true, lastComponent);
          me.consume();
          return;
        }
      }

      Component component = me.getComponent();
      if (component instanceof HighlightComponent) return;
      me.consume();
      //Component component = FocusManager.getCurrentManager().getFocusedWindow().getComponentAt(me.getLocationOnScreen());


      Point mousePoint = me.getPoint();
      if (component instanceof JFrame) {
        JLayeredPane layeredPane = ((JFrame)component).getLayeredPane();
        Point pt = SwingUtilities.convertPoint(component, mousePoint, layeredPane);
        component = layeredPane.findComponentAt(pt);
      } else if (component instanceof Container) {
        component = ((Container)component).findComponentAt(mousePoint);
      }

      if (component instanceof HighlightComponent) return;
      else if (component == null) {
        component = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      }
      if (component != null) {
        //user click
        if (me.getClickCount() == 1 && me.getID() == MOUSE_CLICKED && me.getButton() == BUTTON1) {
          if (clickAction != null) {
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("Component", component);
            if (component instanceof JBList)
              dataMap.put("ItemName", getCellText((JBList)component, mousePoint));
            DataContext dataContext = SimpleDataContext.getSimpleContext(dataMap, null);
            AnActionEvent event = AnActionEvent.createFromDataContext(UI_DROPPER_PLACE, null, dataContext);
            clickAction.actionPerformed(event);
            return;
          }
        }
        lastComponent = component;
        if (component instanceof JBList) {
          highlightJBListCell(((JBList)component), mousePoint);
          return;
        }
        highlightCmp(true, component);
      }
    }

    private static void processContainerEvent(ContainerEvent event) {
      Component child = event.getID() == ContainerEvent.COMPONENT_ADDED ? event.getChild() : null;
      if (child instanceof JComponent && !(event.getSource() instanceof CellRendererPane)) {
        String text = ExceptionUtil.getThrowableText(new Throwable());
        int first = text.indexOf("at com.intellij", text.indexOf("at java.awt"));
        int last = text.indexOf("at java.awt.EventQueue");
        if (last == -1) last = text.length();
        String val = last > first && first > 0 ? text.substring(first, last).trim() : null;
        ((JComponent)child).putClientProperty("uiInspector.addedAt", val);
      }
    }
  }

  /**
   * @noinspection UseJBColor
   */
  private static Object fromObject(Object o, Class<?> type) {
    if (o == null) return null;
    if (type.isAssignableFrom(o.getClass())) return o;
    if ("null".equals(o)) return null;

    String value = String.valueOf(o).trim();
    if (type == int.class) return Integer.parseInt(value);
    if (type == boolean.class) return "yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
    if (type == byte.class) return Byte.parseByte(value);
    if (type == short.class) return Short.parseShort(value);
    if (type == double.class) return Double.parseDouble(value);
    if (type == float.class) return Float.parseFloat(value);

    String[] s = value.split("(?i)\\s*(?:[x@:]|[a-z]+:)\\s*", 6);
    if (type == Dimension.class) {
      if (s.length == 2) return new Dimension(Integer.parseInt(s[0]), Integer.parseInt(s[1]));
    }
    else if (type == Point.class) {
      if (s.length == 2) return new Point(Integer.parseInt(s[0]), Integer.parseInt(s[1]));
    }
    else if (type == Rectangle.class) {
      if (s.length >= 5) {
        return new Rectangle(Integer.parseInt(s[3]), Integer.parseInt(s[4]),
                             Integer.parseInt(s[1]), Integer.parseInt(s[2]));
      }
    }
    else if (type == Insets.class) {
      if (s.length >= 5) {
        return new Insets(Integer.parseInt(s[1]), Integer.parseInt(s[2]),
                          Integer.parseInt(s[4]), Integer.parseInt(s[4]));
      }
    }
    else if (type == Color.class) {
      if (s.length >= 5) {
        return new ColorUIResource(
          new Color(Integer.parseInt(s[1]), Integer.parseInt(s[2]), Integer.parseInt(s[3]), Integer.parseInt(s[4])));
      }
    }
    throw new UnsupportedOperationException(type.toString());
  }
}
