// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.components;

import com.intellij.icons.AllIcons;
import com.intellij.internal.inspector.UiInspectorAction;
import com.intellij.internal.inspector.UiInspectorUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.RetrievableIcon;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.ColorsIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.UIResource;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Map;

final class ValueCellRenderer implements TableCellRenderer {
  private static final Map<Class<?>, Renderer<?>> RENDERERS = new HashMap<>();

  static {
    RENDERERS.put(Point.class, new PointRenderer());
    RENDERERS.put(Dimension.class, new DimensionRenderer());
    RENDERERS.put(Insets.class, new InsetsRenderer());
    RENDERERS.put(Rectangle.class, new RectangleRenderer());
    RENDERERS.put(Color.class, new ColorRenderer());
    RENDERERS.put(Font.class, new FontRenderer());
    RENDERERS.put(Boolean.class, new BooleanRenderer());
    RENDERERS.put(Icon.class, new IconRenderer());
    RENDERERS.put(Border.class, new BorderRenderer());
  }

  private static final Renderer<Object> DEFAULT_RENDERER = new ObjectRenderer();

  private static final JLabel NULL_RENDERER = new JLabel("-");

  @Override
  public JLabel getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (value == null) {
      NULL_RENDERER.setOpaque(isSelected);
      NULL_RENDERER.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
      NULL_RENDERER.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
      return NULL_RENDERER;
    }

    Renderer<Object> renderer = ObjectUtils.notNull(getRenderer(value.getClass()), DEFAULT_RENDERER);

    renderer.setOpaque(isSelected);
    renderer.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
    renderer.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
    renderer.setValue(value);
    return renderer;
  }

  @Nullable
  private static Renderer<Object> getRenderer(Class<?> clazz) {
    if (clazz == null) return null;

    @SuppressWarnings("unchecked")
    Renderer<Object> renderer = (Renderer<Object>)RENDERERS.get(clazz);
    if (renderer != null) return renderer;

    Class<?>[] interfaces = clazz.getInterfaces();
    for (Class<?> aClass : interfaces) {
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

  private static abstract class Renderer<T> extends JLabel {
    abstract void setValue(@NotNull T value);
  }

  private static class PointRenderer extends Renderer<Point> {
    @Override
    public void setValue(@NotNull final Point value) {
      setText(String.valueOf(value.x) + ':' + value.y);
    }
  }

  private static class DimensionRenderer extends Renderer<Dimension> {
    @Override
    public void setValue(@NotNull final Dimension value) {
      setText(value.width + "x" + value.height);
    }
  }

  private static class InsetsRenderer extends Renderer<Insets> {
    @Override
    public void setValue(@NotNull final Insets value) {
      setText("top: " + value.top + " left:" + value.left + " bottom:" + value.bottom + " right:" + value.right);
    }
  }

  static class RectangleRenderer extends Renderer<Rectangle> {
    @Override
    public void setValue(@NotNull final Rectangle value) {
      setText(toString(value));
    }

    @NotNull
    public static String toString(@NotNull Rectangle r) {
      return r.width + "x" + r.height + " @ " + r.x + ":" + r.y;
    }
  }

  private static class ColorRenderer extends Renderer<Color> {
    @Override
    public void setValue(@NotNull final Color value) {
      StringBuilder sb = new StringBuilder();
      sb.append(" r:").append(value.getRed());
      sb.append(" g:").append(value.getGreen());
      sb.append(" b:").append(value.getBlue());
      sb.append(" a:").append(value.getAlpha());

      sb.append(" argb:0x");
      String hex = Integer.toHexString(value.getRGB());
      sb.append("0".repeat(8 - hex.length()));
      sb.append(StringUtil.toUpperCase(hex));

      if (value instanceof UIResource) sb.append(" UIResource");
      if (value instanceof JBColor) {
        String name = ((JBColor)value).getName();
        if (!StringUtil.isEmpty(name)) {
          sb.append(" name: ").append(name);
        }
      }
      setText(sb.toString());
      setIcon(createColorIcon(value));
    }
  }

  private static class FontRenderer extends Renderer<Font> {
    @Override
    public void setValue(@NotNull final Font value) {
      StringBuilder sb = new StringBuilder();
      sb.append(value.getFontName()).append(" (").append(value.getFamily()).append("), ").append(value.getSize()).append("px");
      if (Font.BOLD == (Font.BOLD & value.getStyle())) sb.append(" bold");
      if (Font.ITALIC == (Font.ITALIC & value.getStyle())) sb.append(" italic");
      if (value instanceof UIResource) sb.append(" UIResource");

      Map<TextAttribute, ?> attributes = value.getAttributes();
      StringBuilder attrs = new StringBuilder();
      for (Map.Entry<TextAttribute, ?> entry : attributes.entrySet()) {
        if (entry.getKey() == TextAttribute.FAMILY || entry.getKey() == TextAttribute.SIZE || entry.getValue() == null) continue;
        if (attrs.length() > 0) attrs.append(",");
        String name = ReflectionUtil.getField(TextAttribute.class, entry.getKey(), String.class, "name");
        attrs.append(name != null ? name : entry.getKey()).append("=").append(entry.getValue());
      }
      if (attrs.length() > 0) {
        sb.append(" {").append(attrs).append("}");
      }

      setText(sb.toString());
    }
  }

  private static class BooleanRenderer extends Renderer<Boolean> {
    @Override
    public void setValue(@NotNull final Boolean value) {
      setText(value ? "Yes" : "No");
    }
  }

  private static class IconRenderer extends Renderer<Icon> {
    @Override
    public void setValue(@NotNull final Icon value) {
      setIcon(value);
      setText(getPathToIcon(value));
    }

    private static String getPathToIcon(@NotNull Icon value) {
      if (value instanceof RetrievableIcon) {
        Icon icon = ((RetrievableIcon)value).retrieveIcon();
        if (icon != value) {
          return getPathToIcon(icon);
        }
      }
      String text = getToStringValue(value);
      if (text.startsWith("jar:") && text.contains("!")) {
        int index = text.lastIndexOf("!");
        String jarFile = text.substring(4, index);
        String path = text.substring(index + 1);

        return path + " in " + jarFile;
      }
      return text;
    }
  }

  private static class BorderRenderer extends Renderer<Border> {
    @Override
    public void setValue(@NotNull final Border value) {
      setText(getTextDescription(value));

      if (value instanceof CompoundBorder) {
        Color insideColor = getBorderColor(((CompoundBorder)value).getInsideBorder());
        Color outsideColor = getBorderColor(((CompoundBorder)value).getOutsideBorder());
        if (insideColor != null && outsideColor != null) {
          setIcon(createColorIcon(insideColor, outsideColor));
        }
        else if (insideColor != null) {
          setIcon(createColorIcon(insideColor));
        }
        else if (outsideColor != null) {
          setIcon(createColorIcon(outsideColor));
        }
        else {
          setIcon(null);
        }
      }
      else {
        Color color = getBorderColor(value);
        setIcon(color != null ? createColorIcon(color) : null);
      }
    }

    @Nullable
    private static Color getBorderColor(@Nullable Border value) {
      if (value instanceof LineBorder) {
        return ((LineBorder)value).getLineColor();
      }
      else if (value instanceof CustomLineBorder) {
        try {
          return (Color)ReflectionUtil.findField(CustomLineBorder.class, Color.class, "myColor").get(value);
        }
        catch (Exception ignore) {
        }
      }

      return null;
    }

    @NotNull
    private static String getTextDescription(@Nullable Border value) {
      if (value == null) {
        return "null";
      }

      StringBuilder sb = new StringBuilder();
      sb.append(UiInspectorUtil.getClassName(value));

      Color color = getBorderColor(value);
      if (color != null) sb.append(" color=").append(color);

      if (value instanceof LineBorder) {
        if (((LineBorder)value).getRoundedCorners()) sb.append(" roundedCorners=true");
      }
      if (value instanceof TitledBorder) {
        sb.append(" title='").append(((TitledBorder)value).getTitle()).append("'");
      }
      if (value instanceof CompoundBorder) {
        sb.append(" inside={").append(getTextDescription(((CompoundBorder)value).getInsideBorder())).append("}");
        sb.append(" outside={").append(getTextDescription(((CompoundBorder)value).getOutsideBorder())).append("}");
      }
      if (value instanceof EmptyBorder) {
        Insets insets = ((EmptyBorder)value).getBorderInsets();
        sb.append(" insets={top=").append(insets.top)
          .append(" left=").append(insets.left)
          .append(" bottom=").append(insets.bottom)
          .append(" right=").append(insets.right)
          .append("}");
      }

      if (value instanceof UIResource) sb.append(" UIResource");
      sb.append(" (").append(getToStringValue(value)).append(")");

      return sb.toString();
    }
  }

  private static class ObjectRenderer extends Renderer<Object> {
    {
      putClientProperty("html.disable", Boolean.TRUE);
    }

    @Override
    public void setValue(@NotNull final Object value) {
      setText(getToStringValue(value));
      setIcon(getText().contains("$$$setupUI$$$") ? AllIcons.FileTypes.UiForm : null);
      if (!getText().equals(getText().trim())) {
        setForeground(JBColor.RED);
      }
    }
  }

  @NotNull
  public static String getToStringValue(@NotNull Object value) {
    StringBuilder sb = new StringBuilder();
    if (value.getClass().getName().equals("javax.swing.ArrayTable")) {
      Map<Object, Object> properties = parseClientProperties(value);
      if (properties != null) {
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
          if (entry.getKey().equals(UiInspectorAction.ADDED_AT_STACKTRACE)) continue;
          if (sb.length() > 0) sb.append(",");
          sb.append('[').append(entry.getKey()).append("->").append(entry.getValue()).append(']');
        }
      }
      if (sb.length() == 0) sb.append("-");
      value = sb;
    }
    if (value.getClass().isArray()) {
      int length = Array.getLength(value);
      for (int index = 0; index < length; index++) {
        if (sb.length() > 0) sb.append(", ");
        Object obj = Array.get(value, index);
        if (obj != null) {
          sb.append(obj.getClass().getName());
        }
      }
      value = sb.length() == 0 ? "-" : sb;
    }
    String toString = StringUtil.notNullize(String.valueOf(value), "toString()==null");
    return toString.replace('\n', ' ');
  }

  @Nullable
  public static Map<Object, Object> parseClientProperties(@NotNull Object value) {
    Object table = ReflectionUtil.getField(value.getClass(), value, Object.class, "table");
    if (table instanceof Map) {
      //noinspection unchecked
      return (Map<Object, Object>)table;
    }

    if (table instanceof Object[] arr) {
      Map<Object, Object> properties = new HashMap<>();
      for (int i = 0; i < arr.length; i += 2) {
        if (arr[i].equals(UiInspectorAction.ADDED_AT_STACKTRACE)) continue;
        properties.put(arr[i], arr[i + 1]);
      }
      return properties;
    }
    return null;
  }

  private static ColorIcon createColorIcon(Color color) {
    return JBUIScale.scaleIcon(new ColorIcon(13, 11, color, true));
  }

  private static Icon createColorIcon(Color color1, Color color2) {
    return JBUIScale.scaleIcon(new ColorsIcon(11, color1, color2));
  }
}
