/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.internal.inspector;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.StripeTable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: spLeaner
 */
public class UiInspectorAction extends ToggleAction implements DumbAware {

  private UiInspector myInspector = null;

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myInspector != null;
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    if (state) {
      if (myInspector == null) {
        myInspector = new UiInspector();
        Toolkit.getDefaultToolkit().addAWTEventListener(myInspector, AWTEvent.MOUSE_EVENT_MASK);
      }
    }
    else {
      if (myInspector != null) {
        Toolkit.getDefaultToolkit().removeAWTEventListener(myInspector);
        Disposer.dispose(myInspector);
        myInspector = null;
      }
    }
  }

  private static class InspectorWindow extends JFrame {
    private InspectorTable myInspectorTable;
    private UiInspector myUiInspector;
    private JComponent myComponent;
    private boolean myHighlighted = false;
    private HighlightComponent myHighlightComponent;

    private InspectorWindow(@NotNull final JComponent component, UiInspector uiInspector) throws HeadlessException {
      myComponent = component;
      myUiInspector = uiInspector;
      getRootPane().setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

      setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

      setLayout(new BorderLayout());
      final String simpleName = component.getClass().getSimpleName();
      setTitle(simpleName.length() == 0 ? component.getClass().getName() : simpleName);

      JToolBar bar = new JToolBar(JToolBar.HORIZONTAL);
      bar.setFloatable(false);
      bar.add(new AbstractAction("Show parent", IconLoader.getIcon("/nodes/parentsIntroduction.png")) {
        public void actionPerformed(ActionEvent e) {
          Container parent = component.getParent();
          if (parent instanceof JComponent) {
            myUiInspector.showInspector((JComponent) parent);
          }
        }

        @Override
        public boolean isEnabled() {
          return myComponent.getParent() != null;
        }
      });

      bar.add(new AbstractAction("Highlight", IconLoader.getIcon("/toolbar/unknown.png")) {
        public void actionPerformed(ActionEvent e) {
          myHighlighted = !myHighlighted;
          highlight(myComponent, !myHighlighted);
        }

        @Override
        public boolean isEnabled() {
          return myComponent.isVisible();
        }
      });

      bar.addSeparator();

      bar.add(new AbstractAction("Refresh", IconLoader.getIcon("/vcs/refresh.png")) {
        public void actionPerformed(ActionEvent e) {
          myInspectorTable.refresh();
        }

        @Override
        public boolean isEnabled() {
          return myComponent.isVisible();
        }
      });

      add(bar, BorderLayout.NORTH);

      add(new JLabel(component.getClass().getName()), BorderLayout.SOUTH);
      myInspectorTable = new InspectorTable(component);
      add(myInspectorTable, BorderLayout.CENTER);

      addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent e) {
          myUiInspector.closed(InspectorWindow.this);
          close();
        }
      });

      getRootPane().getActionMap().put("CLOSE", new AbstractAction() {
        public void actionPerformed(ActionEvent e) {
          myUiInspector.closed(InspectorWindow.this);
          close();
          InspectorWindow.this.setVisible(false);
          InspectorWindow.this.dispose();
        }
      });

      getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "CLOSE");
    }
    
    public void close() {
      highlight(myComponent, true);
      myComponent = null;
    }

    private void highlight(final Component c, final boolean clear) {
      if (c != null) {
        final JRootPane rootPane = SwingUtilities.getRootPane(c);
        if (rootPane != null) {
          final JComponent glassPane = (JComponent)rootPane.getGlassPane();

          if (clear) {
            if (myHighlightComponent != null) {
              glassPane.remove(myHighlightComponent);
              myHighlightComponent = null;
              glassPane.revalidate();
              glassPane.repaint();
            }
          } else {
            myHighlightComponent = new HighlightComponent(Color.GREEN);

            final Point pt = SwingUtilities.convertPoint(c, new Point(0, 0), rootPane);
            myHighlightComponent.setBounds(pt.x, pt.y, c.getWidth(), c.getHeight());
            glassPane.add(myHighlightComponent);

            glassPane.revalidate();
            glassPane.repaint();
          }
        }
      }
    }

    public JComponent getComponent() {
      return myComponent;
    }
  }

  private static class HighlightComponent extends JComponent {
    private Color myColor;

    private HighlightComponent(@NotNull final Color c) {
      myColor = c;
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
  }

  private static class InspectorTable extends JPanel {
    private InspectorTableModel myModel;
    private DimensionsComponent myDimensionComponent;

    private InspectorTable(@NotNull final JComponent component) {
      setLayout(new BorderLayout());

      myModel = new InspectorTableModel(component);
      final StripeTable table = new StripeTable(myModel);

      TableColumnModel columnModel = table.getColumnModel();
      TableColumn propertyColumn = columnModel.getColumn(0);
      propertyColumn.setMinWidth(150);
      propertyColumn.setMaxWidth(150);
      propertyColumn.setResizable(false);

      TableColumn valueColumn = columnModel.getColumn(1);
      valueColumn.setMinWidth(200);
      valueColumn.setResizable(false);
      valueColumn.setCellRenderer(new ValueCellRenderer());

      table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

      add(StripeTable.createScrollPane(table), BorderLayout.CENTER);
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
    private JComponent myComponent;
    private int myWidth;
    private int myHeight;
    private Border myBorder;
    private Insets myInsets;

    private DimensionsComponent(@NotNull final JComponent component) {
      myComponent = component;
      setOpaque(true);
      setBackground(Color.WHITE);
      setBorder(new EmptyBorder(5, 0, 5, 0));

      setFont(new JLabel().getFont().deriveFont(Font.PLAIN, 9));

      update();
    }

    public void update() {
      myWidth = myComponent.getWidth();
      myHeight = myComponent.getHeight();
      myBorder = myComponent.getBorder();
      myInsets = myComponent.getInsets();
    }

    @Override
    protected void paintComponent(final Graphics g) {
      Graphics2D g2d = (Graphics2D)g;
      Rectangle bounds = getBounds();

      g2d.setColor(getBackground());
      Insets insets = getInsets();
      g2d.fillRect(insets.left, insets.top, bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom);
      g2d.setColor(getForeground());

      final String sizeString = new StringBuilder().append(myWidth).append(" x ").append(myHeight).toString();

      FontMetrics fm = g2d.getFontMetrics();
      int sizeWidth = fm.stringWidth(sizeString);

      int fontHeight = fm.getHeight();

      g2d.drawString(sizeString, bounds.width / 2 - sizeWidth / 2, bounds.height / 2 + fontHeight / 2);

      g2d.setColor(Color.GRAY);

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

    private static void drawInsets(Graphics2D g2d, FontMetrics fm, String name, Insets insets, int offset, int fontHeight, int innerX, int innerY, int innerWidth, int innerHeight) {
      g2d.setColor(Color.BLACK);
      g2d.drawString(name, innerX - offset + 5, innerY - offset + fontHeight);

      g2d.setColor(Color.GRAY);
      int dashWidth = fm.stringWidth("-");

      if (insets != null) {
        final String top = Integer.toString(insets.top);
        final String bottom = Integer.toString(insets.bottom);
        final String left = Integer.toString(insets.left);
        final String right = Integer.toString(insets.right);

        g2d.drawString(top, innerX - offset + ((innerWidth + offset * 2) / 2 - fm.stringWidth(top) / 2), innerY - offset + fontHeight);
        g2d.drawString(bottom, innerX - offset + ((innerWidth + offset * 2) / 2 - fm.stringWidth(bottom) / 2), innerY - offset  + innerHeight + offset*2 - 8 + fontHeight / 2);
        g2d.drawString(left, innerX - offset + 7 - fm.stringWidth(left) / 2, innerY - offset + (innerHeight + offset * 2) / 2 + fontHeight / 2);
        g2d.drawString(right, innerX + innerWidth + offset - 7 - fm.stringWidth(right) / 2, innerY - offset + (innerHeight + offset * 2) / 2 + fontHeight / 2);
      } else {
        g2d.drawString("-", innerX - offset + ((innerWidth + offset * 2) / 2 - dashWidth / 2), innerY - offset + fontHeight);
        g2d.drawString("-", innerX - offset + ((innerWidth + offset * 2) / 2 - dashWidth / 2), innerY - offset  + innerHeight + offset*2 - 8 + fontHeight / 2);
        g2d.drawString("-", innerX - offset + 7 - dashWidth / 2, innerY - offset + (innerHeight + offset * 2) / 2 + fontHeight / 2);
        g2d.drawString("-", innerX + innerWidth + offset - 7 - dashWidth / 2, innerY - offset + (innerHeight + offset * 2) / 2 + fontHeight / 2);
      }
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(120, 120);
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(150, 150);
    }
  }

  private static class ValueCellRenderer implements TableCellRenderer {
    private static final Map<Class, Renderer> RENDERERS = new HashMap<Class, Renderer>();

    static {
      RENDERERS.put(Point.class, new PointRenderer());
      RENDERERS.put(Dimension.class, new DimensionRenderer());
      RENDERERS.put(Insets.class, new InsetsRenderer());
      RENDERERS.put(Rectangle.class, new RectangleRenderer());
      RENDERERS.put(Color.class, new ColorRenderer());
      RENDERERS.put(ColorUIResource.class, new ColorRenderer());
      RENDERERS.put(Font.class, new FontRenderer());
      RENDERERS.put(Boolean.class, new BooleanRenderer());
    }

    private static final Renderer DEFAULT_RENDERER = new ObjectRenderer();

    private static final JLabel NULL_RENDERER = new JLabel("-");

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (value == null) {
        NULL_RENDERER.setOpaque(false);
        NULL_RENDERER.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
        NULL_RENDERER.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        return NULL_RENDERER;
      }

      Renderer renderer = RENDERERS.get(value.getClass());
      if (renderer == null) {
        if (value instanceof Font) {
          renderer = RENDERERS.get(Font.class);
        } else if (value instanceof Color) {
          renderer = RENDERERS.get(Color.class);
        } else {
          renderer = DEFAULT_RENDERER;
        }
      }

      JComponent result = renderer.setValue(value);
      result.setOpaque(false);
      result.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
      result.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
      return result;
    }
  }

  private interface Renderer<T> {                              
    JComponent setValue(@NotNull T value);
  }

  private static class PointRenderer extends JLabel implements Renderer<Point> {
    public JComponent setValue(@NotNull final Point value) {
      setText(new StringBuilder().append(value.x).append(':').append(value.y).toString());
      return this;
    }
  }

  private static class DimensionRenderer extends JLabel implements Renderer<Dimension> {
    public JComponent setValue(@NotNull final Dimension value) {
      setText(new StringBuilder().append(value.width).append(" x ").append(value.height).toString());
      return this;
    }
  }

  private static class InsetsRenderer extends JLabel implements Renderer<Insets> {
    public JComponent setValue(@NotNull final Insets value) {
      setText(new StringBuilder("top: ").append(value.top).append(" left:").append(value.left).append(" bottom:").append(value.bottom)
        .append(" right:").append(value.right).toString());
      return this;
    }
  }

  private static class RectangleRenderer extends JLabel implements Renderer<Rectangle> {
    public JComponent setValue(@NotNull final Rectangle value) {
      setText(new StringBuilder().append(value.x).append(":").append(value.y).append(", ").append(value.width)
        .append(" x ").append(value.height).toString());
      return this;
    }
  }

  private static class ColorRenderer extends JLabel implements Renderer<Color> {
    public JComponent setValue(@NotNull final Color value) {
      setText(new StringBuilder("r:").append(value.getRed()).append(", g:").append(value.getGreen()).append(", b:").append(value.getBlue())
        .toString());
      return this;
    }
  }

  private static class FontRenderer extends JLabel implements Renderer<Font> {
    public JComponent setValue(@NotNull final Font value) {
      setText(new StringBuilder(value.getFontName()).append(" (").append(value.getFamily()).append("), ").append(value.getSize()).
        append("px").toString());
      return this;
    }
  }

  private static class BooleanRenderer extends JLabel implements Renderer<Boolean> {
    public JComponent setValue(@NotNull final Boolean value) {
      setText(value ? "Yes" : "No");
      return this;
    }
  }

  private static class ObjectRenderer extends JLabel implements Renderer<Object> {
    public JComponent setValue(@NotNull final Object value) {
      setText(value.toString());
      return this;
    }
  }

  private static class PropertyBean {
    private PropertyBean(String propertyName, Object propertyValue, boolean componentMethod) {
      this.propertyName = propertyName;
      this.propertyValue = propertyValue;
      isComponentMethod = componentMethod;
    }

    public String propertyName;
    public Object propertyValue;
    public boolean isComponentMethod;
  }

  private static class InspectorTableModel extends AbstractTableModel {

    private static final String[] JCOMPONENT_METHODS = new String[] {
      "getLocation", "getLocationOnScreen", "getPreferredSize", "getMinimumSize", "getMaximumSize",
      "getAlignmentX", "getAlignmentY", "getTooltipText", "getVisibleRect", "getLayout",
      "getForeground", "getBackground", "getFont", "isOpaque", "isFocusCycleRoot", "isValid", "isDisplayable",
      "isShowing", "isEnabled", "isLightweight", "isFocusable", "isFocusOwner"
    };

    private JComponent myComponent;
    private List<PropertyBean> myProperties = new ArrayList<PropertyBean>();

    public InspectorTableModel(@NotNull final JComponent c) {
      myComponent = c;

      fillTable();
    }

    private void fillTable() {
      final Class<? extends JComponent> cls = myComponent.getClass();
      for (final String methodName: JCOMPONENT_METHODS) {
        try {
          final Method method = cls.getMethod(methodName);
          final Object result = method.invoke(myComponent);

          final String propertyName = methodName.startsWith("is") ? StringUtil.decapitalize(methodName.substring(2)) : StringUtil.decapitalize(methodName.substring(3));
          myProperties.add(new PropertyBean(propertyName, result, true));
        }
        catch (NoSuchMethodException e) {
          // skip
        }
        catch (InvocationTargetException e) {
          // skip
        }
        catch (IllegalAccessException e) {
          // skip
        }
      }
    }

    public Object getValueAt(int row, int column) {
      final PropertyBean bean = myProperties.get(row);
      if (bean != null) {
        switch (column) {
          case 0:
            return bean.propertyName;
          default:
            return bean.propertyValue;
        }
      }

      return null;
    }

    public int getColumnCount() {
      return 2;
    }

    public int getRowCount() {
      return myProperties.size();
    }

    public String getColumnName(int columnIndex) {
      return columnIndex == 0 ? "Property" : "Value";
    }

    public Class<?> getColumnClass(int columnIndex) {
      return columnIndex == 0 ? String.class : Object.class;
    }

    public void refresh() {
      myProperties.clear();
      fillTable();
      fireTableDataChanged();
    }
  }

  private static class UiInspector implements AWTEventListener, Disposable {
    private Map<JComponent, InspectorWindow> myComponentToInspector = new HashMap<JComponent, InspectorWindow>(); 

    public void dispose() {
      for (final JComponent c : myComponentToInspector.keySet()) {
        final InspectorWindow window = myComponentToInspector.get(c);
        window.close();
      }

      myComponentToInspector.clear();
    }

    public void showInspector(@NotNull final JComponent c) {
      InspectorWindow window = myComponentToInspector.get(c);
      if (window != null) {
        window.setVisible(true);
        window.toFront();
      } else {
        window = new InspectorWindow(c, this);
        myComponentToInspector.put(c, window);
        window.pack();
        window.setVisible(true);
      }
    }

    public void eventDispatched(final AWTEvent event) {
      if (event instanceof MouseEvent) {
        final MouseEvent me = (MouseEvent)event;
        if (me.isAltDown() && me.isControlDown()) {
          switch (me.getID()) {
            case MouseEvent.MOUSE_CLICKED:
              if (me.getClickCount() == 1 && !me.isPopupTrigger()) {
                Object source = me.getSource();
                if (source instanceof JComponent) showInspector((JComponent) source);
                me.consume();
              }

              break;
            default:
              break;
          }
        }
      }
    }

    public void closed(final InspectorWindow inspectorWindow) {
      JComponent c = inspectorWindow.getComponent();
      myComponentToInspector.remove(c);
    }
  }
}
