// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.scale.DerivedScaleType;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.Scale;
import com.intellij.ui.scale.UserScaleContext;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.lang.ref.WeakReference;

/**
 * @author Konstantin Bulenkov
 * @author tav
 */
@SuppressWarnings("UseJBColor")
public class JBUI {
  @Deprecated
  public static float sysScale() {
    return JBUIScale.sysScale();
  }

  @Deprecated
  public static float sysScale(@Nullable Graphics2D g) {
    return JBUIScale.sysScale(g);
  }

  @Deprecated
  public static float sysScale(@Nullable Component comp) {
    return JBUIScale.sysScale(comp);
  }

  /**
   * Returns the pixel scale factor, corresponding to the default monitor device.
   */
  public static float pixScale() {
    return JreHiDpiUtil.isJreHiDPIEnabled() ? JBUIScale.sysScale() * JBUIScale.scale(1f) : JBUIScale.scale(1f);
  }

  /**
   * Returns "f" scaled by pixScale().
   */
  public static float pixScale(float f) {
    return pixScale() * f;
  }

  /**
   * Returns the pixel scale factor, corresponding to the provided configuration.
   * In the IDE-managed HiDPI mode defaults to {@link #pixScale()}
   */
  public static float pixScale(@Nullable GraphicsConfiguration gc) {
    return JreHiDpiUtil.isJreHiDPIEnabled() ? JBUIScale.sysScale(gc) * JBUIScale.scale(1f) : JBUIScale.scale(1f);
  }

  /**
   * Returns the pixel scale factor, corresponding to the device the provided component is tied to.
   * In the IDE-managed HiDPI mode defaults to {@link #pixScale()}
   */
  public static float pixScale(@Nullable Component comp) {
    return pixScale(comp != null ? comp.getGraphicsConfiguration() : null);
  }

  @Deprecated
  public static float setUserScaleFactor(float scale) {
    return JBUIScale.setUserScaleFactor(scale);
  }

  @Deprecated
  public static float scale(float f) {
    return JBUIScale.scale(f);
  }

  /**
   * @return 'i' scaled by the user scale factor
   */
  public static int scale(int i) {
    return JBUIScale.scale(i);
  }

  public static int scaleFontSize(float fontSize) {
    return JBUIScale.scaleFontSize(fontSize);
  }

  @NotNull
  public static JBValue value(float value) {
    return new JBValue.Float(value);
  }

  @NotNull
  public static JBValue uiIntValue(@NotNull String key, int defValue) {
    return new JBValue.UIInteger(key, defValue);
  }

  @NotNull
  public static JBDimension size(int width, int height) {
    return new JBDimension(width, height);
  }

  @NotNull
  public static JBDimension size(int widthAndHeight) {
    return new JBDimension(widthAndHeight, widthAndHeight);
  }

  @NotNull
  public static JBDimension size(Dimension size) {
    if (size instanceof JBDimension) {
      JBDimension newSize = ((JBDimension)size).newSize();
      return size instanceof UIResource ? newSize.asUIResource() : newSize;
    }
    return new JBDimension(size.width, size.height);
  }

  @NotNull
  public static JBInsets insets(int top, int left, int bottom, int right) {
    return new JBInsets(top, left, bottom, right);
  }

  @NotNull
  public static JBInsets insets(int all) {
    return new JBInsets(all, all, all, all);
  }

  @NotNull
  public static JBInsets insets(String propName, JBInsets defaultValue) {
    Insets i = UIManager.getInsets(propName);
    return i != null ? JBInsets.create(i) : defaultValue;
  }

  @NotNull
  public static JBInsets insets(int topBottom, int leftRight) {
    return JBInsets.create(topBottom, leftRight);
  }

  @NotNull
  public static JBInsets emptyInsets() {
    return new JBInsets(0, 0, 0, 0);
  }

  @NotNull
  public static JBInsets insetsTop(int t) {
    return new JBInsets(t, 0, 0, 0);
  }

  @NotNull
  public static JBInsets insetsLeft(int l) {
    return new JBInsets(0, l, 0, 0);
  }

  @NotNull
  public static JBInsets insetsBottom(int b) {
    return new JBInsets(0, 0, b, 0);
  }

  @NotNull
  public static JBInsets insetsRight(int r) {
    return new JBInsets(0, 0, 0, r);
  }

  @NotNull
  public static <T extends JBScalableIcon> T scale(@NotNull T icon) {
    //noinspection unchecked
    return (T)icon.withIconPreScaled(false);
  }

  /**
   * @deprecated Use {@link #scale(JBScalableIcon)}.
   */
  @Deprecated
  @NotNull
  public static <T extends JBIcon> T scale(@NotNull T icon) {
    //noinspection unchecked
    return (T)icon.withIconPreScaled(false);
  }

  @NotNull
  public static JBDimension emptySize() {
    return new JBDimension(0, 0);
  }

  @NotNull
  public static JBInsets insets(@NotNull Insets insets) {
    return JBInsets.create(insets);
  }

  /**
   * @deprecated use {@link JBUIScale#isUsrHiDPI()} instead
   */
  @Deprecated
  public static boolean isHiDPI() {
    return JBUIScale.isUsrHiDPI();
  }

  @Deprecated
  public static boolean isUsrHiDPI() {
    return JBUIScale.isUsrHiDPI();
  }

  /**
   * Returns whether the {@link DerivedScaleType#PIX_SCALE} scale factor assumes HiDPI-awareness in the provided graphics config.
   * An equivalent of {@code isHiDPI(pixScale(gc))}
   */
  public static boolean isPixHiDPI(@Nullable GraphicsConfiguration gc) {
    return JBUIScale.isHiDPI(pixScale(gc));
  }

  /**
   * Returns whether the {@link DerivedScaleType#PIX_SCALE} scale factor assumes HiDPI-awareness in the provided component's device.
   * An equivalent of {@code isHiDPI(pixScale(comp))}
   */
  public static boolean isPixHiDPI(@Nullable Component comp) {
    return JBUIScale.isHiDPI(pixScale(comp));
  }

  public static class Fonts {
    @NotNull
    public static JBFont label() {
      return JBFont.label();
    }

    @NotNull
    public static JBFont label(float size) {
      return JBFont.label().deriveFont(JBUIScale.scale(size));
    }

    @NotNull
    public static JBFont smallFont() {
      return JBFont.label().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL));
    }

    @NotNull
    public static JBFont miniFont() {
      return JBFont.label().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.MINI));
    }

    @NotNull
    public static JBFont create(String fontFamily, int size) {
      return JBFont.create(new Font(fontFamily, Font.PLAIN, size));
    }

    @NotNull
    public static JBFont toolbarFont() {
      return SystemInfo.isMac ? smallFont() : JBFont.label();
    }

    @NotNull
    public static JBFont toolbarSmallComboBoxFont() {
      return label(11);
    }
  }

  private static final JBEmptyBorder SHARED_EMPTY_INSTANCE = new JBEmptyBorder(0);

  @SuppressWarnings("UseDPIAwareBorders")
  public static class Borders {
    @NotNull
    public static JBEmptyBorder empty(int top, int left, int bottom, int right) {
      if (top == 0 && left == 0 && bottom == 0 && right == 0) {
        return SHARED_EMPTY_INSTANCE;
      }
      return new JBEmptyBorder(top, left, bottom, right);
    }

    @NotNull
    public static JBEmptyBorder empty(int topAndBottom, int leftAndRight) {
      return empty(topAndBottom, leftAndRight, topAndBottom, leftAndRight);
    }

    @NotNull
    public static JBEmptyBorder emptyTop(int offset) {
      return empty(offset, 0, 0, 0);
    }

    @NotNull
    public static JBEmptyBorder emptyLeft(int offset) {
      return empty(0, offset,  0, 0);
    }

    @NotNull
    public static JBEmptyBorder emptyBottom(int offset) {
      return empty(0, 0, offset, 0);
    }

    @NotNull
    public static JBEmptyBorder emptyRight(int offset) {
      return empty(0, 0, 0, offset);
    }

    @NotNull
    public static JBEmptyBorder empty() {
      return empty(0, 0, 0, 0);
    }

    @NotNull
    public static Border empty(int offsets) {
      return empty(offsets, offsets, offsets, offsets);
    }

    @NotNull
    public static Border customLine(Color color, int top, int left, int bottom, int right) {
      return new CustomLineBorder(color, insets(top, left, bottom, right));
    }

    @NotNull
    public static Border customLine(Color color, int thickness) {
      return customLine(color, thickness, thickness, thickness, thickness);
    }

    @NotNull
    public static Border customLine(Color color) {
      return customLine(color, 1);
    }

    @NotNull
    public static Border merge(@Nullable Border source, @NotNull Border extra, boolean extraIsOutside) {
      if (source == null) return extra;
      return new CompoundBorder(extraIsOutside ? extra : source, extraIsOutside? source : extra);
    }
  }

  public static class Panels {
    @NotNull
    public static BorderLayoutPanel simplePanel() {
      return new BorderLayoutPanel();
    }

    @NotNull
    public static BorderLayoutPanel simplePanel(Component comp) {
      return simplePanel().addToCenter(comp);
    }

    @NotNull
    public static BorderLayoutPanel simplePanel(int hgap, int vgap) {
      return new BorderLayoutPanel(hgap, vgap);
    }
  }

  public static Border asUIResource(@NotNull Border border) {
    if (border instanceof UIResource) return border;
    return new BorderUIResource(border);
  }

  @SuppressWarnings("UnregisteredNamedColor")
  public static final class CurrentTheme {
    public static class ActionButton {
      @NotNull
      public static Color pressedBackground() {
        return JBColor.namedColor("ActionButton.pressedBackground", Gray.xCF);
      }

      @NotNull
      public static Color pressedBorder() {
        return JBColor.namedColor("ActionButton.pressedBorderColor", Gray.xCF);
      }

      @NotNull
      public static Color hoverBackground() {
        return JBColor.namedColor("ActionButton.hoverBackground", Gray.xDF);
      }

      @NotNull
      public static Color hoverBorder() {
        return JBColor.namedColor("ActionButton.hoverBorderColor", Gray.xDF);
      }

      @NotNull
      public static Color hoverSeparatorColor() {
        return JBColor.namedColor("ActionButton.hoverSeparatorColor", new JBColor(Gray.xB3, Gray.x6B));
      }
    }

    public static class CustomFrameDecorations {
      @NotNull
      public static Color separatorForeground() {
        return JBColor.namedColor("Separator.separatorColor", new JBColor(0xcdcdcd, 0x515151));
      }

      @NotNull
      public static Color titlePaneBackground() {
        return JBColor.namedColor("TitlePane.background", paneBackground());
      }

      @NotNull
      public static Color titlePaneInfoForeground() {
        return JBColor.namedColor("TitlePane.infoForeground", new JBColor(0x595959, 0x999999));
      }

      @NotNull
      public static Color titlePaneInactiveInfoForeground() {
        return JBColor.namedColor("TitlePane.inactiveInfoForeground", new JBColor(0xB1B1B1, 0x737373));
      }

      @NotNull
      public static Color paneBackground() {
        return JBColor.namedColor("Panel.background", Gray.xCD);
      }
    }

    public static class DefaultTabs {
      @NotNull
      public static Color underlineColor() {
        return JBColor.namedColor("DefaultTabs.underlineColor", new JBColor(0x4083C9, 0x4A88C7));
      }

      public static int underlineHeight() {
        return getInt("DefaultTabs.underlineHeight", JBUIScale.scale(3));
      }

      @NotNull
      public static Color inactiveUnderlineColor() {
        return JBColor.namedColor("DefaultTabs.inactiveUnderlineColor", new JBColor(0x9ca7b8, 0x747a80));
      }

      @NotNull
      public static Color borderColor() {
        return JBColor.namedColor("DefaultTabs.borderColor", UIUtil.CONTRAST_BORDER_COLOR);
      }

      @NotNull
      public static Color background() {
        return JBColor.namedColor("DefaultTabs.background", new JBColor(0xECECEC, 0x3C3F41));
      }

      @NotNull
      public static Color hoverBackground() {
        return JBColor.namedColor("DefaultTabs.hoverBackground",
                                  new JBColor(ColorUtil.withAlpha(Color.BLACK, .10),
                                              ColorUtil.withAlpha(Color.BLACK, .35)));
      }

      public static Color underlinedTabBackground() {
        return UIManager.getColor("DefaultTabs.underlinedTabBackground");
      }

      @NotNull
      public static Color underlinedTabForeground() {
        return JBColor.namedColor("DefaultTabs.underlinedTabForeground", UIUtil.getLabelForeground());
      }

      @NotNull
      public static Color hoverColor() {
        return JBColor.namedColor("DefaultTabs.hoverColor",
                                  new JBColor(0xD9D9D9,
                                              0x2E3133));
      }
    }

    public static class DebuggerTabs {
      public static int underlineHeight() {
        return getInt("DebuggerTabs.underlineHeight", JBUIScale.scale(2));
      }

      public static Color underlinedTabBackground() {
        return UIManager.getColor("DebuggerTabs.underlinedTabBackground");
      }
    }

    public static class EditorTabs {
      @NotNull
      public static Color underlineColor() {
        return JBColor.namedColor("EditorTabs.underlineColor", DefaultTabs.underlineColor());
      }

      public static int underlineHeight() {
        return getInt("EditorTabs.underlineHeight", DefaultTabs.underlineHeight());
      }

      @NotNull
      public static Color inactiveUnderlineColor() {
        return JBColor.namedColor("EditorTabs.inactiveUnderlineColor", DefaultTabs.inactiveUnderlineColor());
      }

      public static Color underlinedTabBackground() {
        return UIManager.getColor("EditorTabs.underlinedTabBackground");
      }

      @NotNull
      public static Color borderColor() {
        return JBColor.namedColor("EditorTabs.borderColor", DefaultTabs.borderColor());
      }

      @NotNull
      public static Color background() {
        return JBColor.namedColor("EditorTabs.background", DefaultTabs.background());
      }

      @NotNull
      public static Color hoverBackground() {
        return JBColor.namedColor("EditorTabs.hoverMaskColor", DefaultTabs.hoverBackground());
      }

      @NotNull
      public static Color hoverColor() {
        return JBColor.namedColor("EditorTabs.hoverColor",
                                  DefaultTabs.hoverColor());
      }

      @NotNull
      public static Color inactiveColoredFileBackground() {
        return JBColor.namedColor("EditorTabs.inactiveColoredFileBackground", new JBColor(ColorUtil.withAlpha(Color.BLACK, .07),
                                                                              ColorUtil.withAlpha(new Color(0x3C3F41), .60)));
      }

      @NotNull
      public static Color underlinedTabForeground() {
        return JBColor.namedColor("EditorTabs.underlinedTabForeground", DefaultTabs.underlinedTabForeground());
      }

    }

    public static class ToolWindow {
      @NotNull
      public static Color borderColor() {
        return JBColor.namedColor("ToolWindow.HeaderTab.borderColor", DefaultTabs.borderColor());
      }

      @NotNull
      public static Color underlinedTabForeground() {
        return JBColor.namedColor("ToolWindow.HeaderTab.underlinedTabForeground", DefaultTabs.underlinedTabForeground());
      }

      @NotNull
      public static Color hoverBackground() {
        return JBColor.namedColor("ToolWindow.HeaderTab.hoverBackground", DefaultTabs.hoverBackground());
      }

      @NotNull
      public static Color inactiveUnderlineColor() {
        return JBColor.namedColor("ToolWindow.HeaderTab.inactiveUnderlineColor", DefaultTabs.inactiveUnderlineColor());
      }

      @NotNull
      public static Color underlineColor() {
        return JBColor.namedColor("ToolWindow.HeaderTab.underlineColor", DefaultTabs.underlineColor());
      }

      public static Color underlinedTabBackground() {
        return UIManager.getColor("ToolWindow.HeaderTab.underlinedTabBackground");
      }

      public static Color hoverInactiveBackground() {
        return JBColor.namedColor("ToolWindow.HeaderTab.hoverInactiveBackground", hoverBackground());
      }

      public static Color underlinedTabInactiveBackground() {
        return UIManager.getColor("ToolWindow.HeaderTab.underlinedTabInactiveBackground");
      }

      @NotNull
      public static Color underlinedTabInactiveForeground() {
        return JBColor.namedColor("ToolWindow.HeaderTab.underlinedTabInactiveForeground", underlinedTabForeground());
      }


      @NotNull
      public static Color tabSelectedBackground() {
        return Registry.is("toolwindow.active.tab.use.contrast.background")
               ? Registry.getColor("toolwindow.active.tab.contrast.background.color", JBColor.GRAY)
               : JBColor.namedColor("ToolWindow.HeaderTab.selectedInactiveBackground",
                                    JBColor.namedColor("ToolWindow.header.tab.selected.background", 0xDEDEDE));
      }

      @NotNull
      public static Color tabSelectedActiveBackground() {
        return Registry.is("toolwindow.active.tab.use.contrast.background")
               ? Registry.getColor("toolwindow.active.tab.contrast.background.color", JBColor.GRAY)
               : JBColor.namedColor("ToolWindow.HeaderTab.selectedBackground",
                                    JBColor.namedColor("ToolWindow.header.tab.selected.active.background", 0xD0D4D8));
      }

      @NotNull
      public static Color tabHoveredBackground() {
        return JBColor.namedColor("ToolWindow.HeaderTab.hoverInactiveBackground",
                                  JBColor.namedColor("ToolWindow.header.tab.hovered.background", tabSelectedBackground()));
      }

      @NotNull
      public static Color tabHoveredActiveBackground() {
        return JBColor.namedColor("ToolWindow.HeaderTab.hoverBackground",
                                  JBColor.namedColor("ToolWindow.header.tab.hovered.active.background", tabSelectedActiveBackground()));
      }

      @NotNull
      public static Color tabSelectedBackground(boolean active) {
        return active ? tabSelectedActiveBackground() : tabSelectedBackground();
      }

      @NotNull
      public static Color tabHoveredBackground(boolean active) {
        return active ? tabHoveredActiveBackground() : tabHoveredBackground();
      }

      @NotNull
      public static Color headerBackground(boolean active) {
        return active ? headerActiveBackground() : headerBackground();
      }

      @NotNull
      public static Color headerBackground() {
        return JBColor.namedColor("ToolWindow.Header.inactiveBackground", JBColor.namedColor("ToolWindow.header.background", 0xECECEC));
      }

      @NotNull
      public static Color headerBorderBackground() {
        return JBColor.namedColor("ToolWindow.Header.borderColor", DefaultTabs.borderColor());
      }

      @NotNull
      public static Color headerActiveBackground() {
        return JBColor.namedColor("ToolWindow.Header.background", JBColor.namedColor("ToolWindow.header.active.background", 0xE2E6EC));
      }

      public static int tabVerticalPaddingOld() {
        return getInt("ToolWindow.tab.verticalPadding", 0);
      }

      public static int tabVerticalPadding() {
        return getInt("ToolWindow.HeaderTab.verticalPadding", JBUIScale.scale(6));
      }

      @NotNull
      @Deprecated
      public static Border tabBorder() {
        return getBorder("ToolWindow.tabBorder", JBUI.Borders.empty(1));
      }

      public static int underlineHeight() {
        return getInt("ToolWindow.HeaderTab.underlineHeight", DefaultTabs.underlineHeight());
      }


      @NotNull
      public static Font headerFont() {
        JBFont font = JBFont.label();
        Object size = UIManager.get("ToolWindow.header.font.size");
        if (size instanceof Integer) {
          return font.deriveFont(((Integer)size).floatValue());
        }
        return font;
      }

      public static float overrideHeaderFontSizeOffset() {
        Object offset = UIManager.get("ToolWindow.overridden.header.font.size.offset");
        if (offset instanceof Integer) {
          return ((Integer)offset).floatValue();
        }

        return 0;
      }

      @NotNull
      public static Color hoveredIconBackground() {
        return JBColor.namedColor("ToolWindow.HeaderCloseButton.background", JBColor.namedColor("ToolWindow.header.closeButton.background", 0xB9B9B9));
      }

      @NotNull
      public static Icon closeTabIcon(boolean hovered) {
        return hovered ? getIcon("ToolWindow.header.closeButton.hovered.icon", AllIcons.Actions.CloseHovered)
                       : getIcon("ToolWindow.header.closeButton.icon", AllIcons.Actions.Close);
      }

      @NotNull
      public static Icon comboTabIcon(boolean hovered) {
        return hovered ? getIcon("ToolWindow.header.comboButton.hovered.icon", AllIcons.General.ArrowDown)
                       : getIcon("ToolWindow.header.comboButton.icon", AllIcons.General.ArrowDown);
      }
    }

    public static class Label {
      @NotNull
      public static Color foreground(boolean selected) {
        return selected ? JBColor.namedColor("Label.selectedForeground", 0xFFFFFF)
                        : JBColor.namedColor("Label.foreground", 0x000000);
      }

      @NotNull
      public static Color foreground() {
        return foreground(false);
      }

      @NotNull
      public static Color disabledForeground(boolean selected) {
        return selected ? JBColor.namedColor("Label.selectedDisabledForeground", 0x999999)
                        : JBColor.namedColor("Label.disabledForeground", JBColor.namedColor("Label.disabledText", 0x999999));
      }

      @NotNull
      public static Color disabledForeground() {
        return disabledForeground(false);
      }
    }

    public static class Popup {
      public static Color headerBackground(boolean active) {
        return active
               ? JBColor.namedColor("Popup.Header.activeBackground", 0xe6e6e6)
               : JBColor.namedColor("Popup.Header.inactiveBackground", 0xededed);
      }

      public static int headerHeight(boolean hasControls) {
        return hasControls ? JBUIScale.scale(28) : JBUIScale.scale(24);
      }

      public static Color borderColor(boolean active) {
        return active
               ? JBColor.namedColor("Popup.borderColor", JBColor.namedColor("Popup.Border.color", 0x808080))
               : JBColor.namedColor("Popup.inactiveBorderColor", JBColor.namedColor("Popup.inactiveBorderColor", 0xaaaaaa));
      }

      public static Color toolbarPanelColor() {
        return JBColor.namedColor("Popup.Toolbar.background", 0xf7f7f7);
      }

      public static Color toolbarBorderColor() {
        return JBColor.namedColor("Popup.Toolbar.borderColor", JBColor.namedColor("Popup.Toolbar.Border.color", 0xf7f7f7));
      }

      public static int toolbarHeight() {
        return JBUIScale.scale(28);
      }

      public static Color separatorColor() {
        return JBColor.namedColor("Popup.separatorColor", new JBColor(Color.gray.brighter(), Gray.x51));
      }

      public static Color separatorTextColor() {
        return JBColor.namedColor("Popup.separatorForeground", Color.gray);
      }
    }

    public static class Focus {
      private static final Color GRAPHITE_COLOR = new JBColor(new Color(0x8099979d, true), new Color(0x676869));

      @NotNull
      public static Color focusColor() {
        return UIUtil.isGraphite() ? GRAPHITE_COLOR : JBColor.namedColor("Component.focusColor", JBColor.namedColor("Focus.borderColor", 0x8ab2eb));
      }

      @NotNull
      public static Color defaultButtonColor() {
        return StartupUiUtil.isUnderDarcula() ? JBColor.namedColor("Button.default.focusColor",
                                    JBColor.namedColor("Focus.defaultButtonBorderColor", 0x97c3f3)) : focusColor();
      }

      @NotNull
      public static Color errorColor(boolean active) {
        return active ? JBColor.namedColor("Component.errorFocusColor", JBColor.namedColor("Focus.activeErrorBorderColor", 0xe53e4d)) :
                        JBColor.namedColor("Component.inactiveErrorFocusColor", JBColor.namedColor("Focus.inactiveErrorBorderColor", 0xebbcbc));
      }

      @NotNull
      public static Color warningColor(boolean active) {
        return active ? JBColor.namedColor("Component.warningFocusColor", JBColor.namedColor("Focus.activeWarningBorderColor", 0xe2a53a)) :
                        JBColor.namedColor("Component.inactiveWarningFocusColor", JBColor.namedColor("Focus.inactiveWarningBorderColor", 0xffd385));
      }
    }

    public static class TabbedPane {
      public static final Color ENABLED_SELECTED_COLOR = JBColor.namedColor("TabbedPane.underlineColor", JBColor.namedColor("TabbedPane.selectedColor", 0x4083C9));
      public static final Color DISABLED_SELECTED_COLOR = JBColor.namedColor("TabbedPane.disabledUnderlineColor", JBColor.namedColor("TabbedPane.selectedDisabledColor", Gray.xAB));
      public static final Color DISABLED_TEXT_COLOR = JBColor.namedColor("TabbedPane.disabledForeground", JBColor.namedColor("TabbedPane.disabledText", Gray.x99));
      public static final Color HOVER_COLOR = JBColor.namedColor("TabbedPane.hoverColor", Gray.xD9);
      public static final Color FOCUS_COLOR = JBColor.namedColor("TabbedPane.focusColor", 0xDAE4ED);
      public static final JBValue TAB_HEIGHT = new JBValue.UIInteger("TabbedPane.tabHeight", 32);
      public static final JBValue SELECTION_HEIGHT = new JBValue.UIInteger("TabbedPane.tabSelectionHeight", 3);
    }

    public static class BigPopup {
      @NotNull
      public static Color headerBackground() {
        return JBColor.namedColor("SearchEverywhere.Header.background", 0xf2f2f2);
      }

      @NotNull
      public static Insets tabInsets() {
        return JBInsets.create(0, 12);
      }

      @NotNull
      public static Color selectedTabColor() {
        return JBColor.namedColor("SearchEverywhere.Tab.selectedBackground", 0xdedede);
      }

      @NotNull
      public static Color selectedTabTextColor() {
        return JBColor.namedColor("SearchEverywhere.Tab.selectedForeground", 0x000000);
      }

      @NotNull
      public static Color searchFieldBackground() {
        return JBColor.namedColor("SearchEverywhere.SearchField.background", 0xffffff);
      }

      @NotNull
      public static Color searchFieldBorderColor() {
        return JBColor.namedColor("SearchEverywhere.SearchField.borderColor", 0xbdbdbd);
      }

      @NotNull
      public static Insets searchFieldInsets() {
        return insets(0, 6, 0, 5);
      }

      public static int maxListHeight() {
        return JBUIScale.scale(600);
      }

      @NotNull
      public static Color listSeparatorColor() {
        return JBColor.namedColor("SearchEverywhere.List.separatorColor", Gray.xDC);
      }

      @NotNull
      public static Color listTitleLabelForeground() {
        return JBColor.namedColor("SearchEverywhere.List.separatorForeground", UIUtil.getLabelDisabledForeground());
      }

      @NotNull
      public static Color searchFieldGrayForeground()  {
        return JBColor.namedColor("SearchEverywhere.SearchField.infoForeground", JBColor.GRAY);
      }

      @NotNull
      public static Color advertiserForeground()  {
        return JBColor.namedColor("SearchEverywhere.Advertiser.foreground", JBColor.GRAY);
      }

      @NotNull
      public static Border advertiserBorder()  {
        return new JBEmptyBorder(insets("SearchEverywhere.Advertiser.foreground", insetsLeft(8)));
      }

      @NotNull
      public static Color advertiserBackground()  {
        return JBColor.namedColor("SearchEverywhere.Advertiser.background", 0xf2f2f2);
      }
    }

    public static class Advertiser {
      private static final JBInsets DEFAULT_AD_INSETS = JBInsets.create(1, 5);

      @NotNull
      public static Color foreground() {
        Color foreground = JBUI.CurrentTheme.BigPopup.advertiserForeground();
        return JBColor.namedColor("Popup.Advertiser.foreground", foreground);
      }

      @NotNull
      public static Color background() {
        Color background = JBUI.CurrentTheme.BigPopup.advertiserBackground();
        return JBColor.namedColor("Popup.Advertiser.background", background);
      }

      @NotNull
      public static Border border() {
        return new JBEmptyBorder(insets("Popup.Advertiser.borderInsets", DEFAULT_AD_INSETS));
      }

      @NotNull
      public static Color borderColor() {
        return JBColor.namedColor("Popup.Advertiser.borderColor", Gray._135);
      }
    }

    public static class Validator {
      @NotNull
      public static Color errorBorderColor() {
        return JBColor.namedColor("ValidationTooltip.errorBorderColor", 0xE0A8A9);
      }

      @NotNull
      public static Color errorBackgroundColor() {
        return JBColor.namedColor("ValidationTooltip.errorBackground", JBColor.namedColor("ValidationTooltip.errorBackgroundColor", 0xF5E6E7));
      }

      @NotNull
      public static Color warningBorderColor() {
        return JBColor.namedColor("ValidationTooltip.warningBorderColor", 0xE0CEA8);
      }

      @NotNull
      public static Color warningBackgroundColor() {
        return JBColor.namedColor("ValidationTooltip.warningBackground", JBColor.namedColor("ValidationTooltip.warningBackgroundColor", 0xF5F0E6));
      }
    }

    public static class Link {
      @NotNull
      public static Color linkColor() {
        return JBColor.namedColor("Link.activeForeground", JBColor.namedColor("link.foreground", 0x589df6));
      }

      @NotNull
      public static Color linkHoverColor() {
        return JBColor.namedColor("Link.hoverForeground", JBColor.namedColor("link.hover.foreground", linkColor()));
      }

      @NotNull
      public static Color linkPressedColor() {
        return JBColor.namedColor("Link.pressedForeground", JBColor.namedColor("link.pressed.foreground", new JBColor(0xf00000, 0xba6f25)));
      }

      @NotNull
      public static Color linkVisitedColor() {
        return JBColor.namedColor("Link.visitedForeground", JBColor.namedColor("link.visited.foreground", new JBColor(0x800080, 0x9776a9)));
      }
    }

    public static class Arrow {
      @NotNull
      public static Color foregroundColor(boolean enabled) {
        return enabled ?
               JBColor.namedColor("ComboBox.ArrowButton.iconColor", JBColor.namedColor("ComboBox.darcula.arrowButtonForeground", Gray.x66)) :
               JBColor.namedColor("ComboBox.ArrowButton.disabledIconColor", JBColor.namedColor("ComboBox.darcula.arrowButtonDisabledForeground", Gray.xAB));

      }

      @NotNull
      public static Color backgroundColor(boolean enabled, boolean editable) {
        return enabled ?
               editable ? JBColor.namedColor("ComboBox.ArrowButton.background", JBColor.namedColor("ComboBox.darcula.editable.arrowButtonBackground", Gray.xFC)) :
               JBColor.namedColor("ComboBox.ArrowButton.nonEditableBackground", JBColor.namedColor("ComboBox.darcula.arrowButtonBackground", Gray.xFC))
                       : UIUtil.getPanelBackground();
      }
    }

    public static class NewClassDialog {
      @NotNull
      public static Color searchFieldBackground() {
        return JBColor.namedColor("NewClass.SearchField.background", 0xffffff);
      }

      @NotNull
      public static Color panelBackground() {
        return JBColor.namedColor("NewClass.Panel.background", 0xf2f2f2);
      }

      @NotNull
      public static Color bordersColor() {
        return JBColor.namedColor(
          "TextField.borderColor",
          JBColor.namedColor("Component.borderColor", new JBColor(0xbdbdbd, 0x646464))
        );
      }

      public static int fieldsSeparatorWidth() {
        return getInt("NewClass.separatorWidth", JBUIScale.scale(10));
      }
    }
  }

  public static int getInt(@NotNull String propertyName, int defaultValue) {
    Object value = UIManager.get(propertyName);
    return value instanceof Integer ? (Integer)value : defaultValue;
  }

  @NotNull
  private static Icon getIcon(@NotNull String propertyName, @NotNull Icon defaultIcon) {
    Icon icon = UIManager.getIcon(propertyName);
    return icon == null ? defaultIcon : icon;
  }

  @NotNull
  private static Border getBorder(@NotNull String propertyName, @NotNull Border defaultBorder) {
    Border border = UIManager.getBorder(propertyName);
    return border == null ? defaultBorder : border;
  }

  /*
   * The scaling classes/methods below are left for binary compatibility with plugins (based on API Watcher).
   */

  /**
   * @deprecated Use {@link com.intellij.ui.scale.ScaleType}.
   */
  @Deprecated
  public enum ScaleType {
    USR_SCALE,
    SYS_SCALE,
    OBJ_SCALE,
    PIX_SCALE
  }

  /**
   * @deprecated Use {@link UserScaleContext}.
   */
  @Deprecated
  public static class BaseScaleContext extends UserScaleContext {
    @SuppressWarnings("MethodOverloadsMethodOfSuperclass")
    public boolean update(@Nullable BaseScaleContext ctx) {
      return super.update(ctx);
    }

    public boolean update(@NotNull Scale scale) {
      return setScale(scale);
    }

    public double getScale(@NotNull ScaleType type) {
      switch (type) {
        case USR_SCALE: return usrScale.value();
        case SYS_SCALE: return 1d;
        case OBJ_SCALE: return objScale.value();
        case PIX_SCALE: return pixScale;
      }
      return 1f; // unreachable
    }
  }

  /**
   * @deprecated Use {@link com.intellij.ui.scale.ScaleContext}.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @SuppressWarnings({"ClassNameSameAsAncestorName", "MethodOverridesStaticMethodOfSuperclass"})
  public static final class ScaleContext extends com.intellij.ui.scale.ScaleContext {
    private ScaleContext() {
    }

    @NotNull
    public static ScaleContext create() {
      return new ScaleContext();
    }

    @NotNull
    public static ScaleContext create(@Nullable Component comp) {
      final ScaleContext ctx = new ScaleContext(com.intellij.ui.scale.ScaleType.SYS_SCALE.of(JBUIScale.sysScale(comp)));
      if (comp != null) ctx.compRef = new WeakReference<>(comp);
      return ctx;
    }

    @NotNull
    public static ScaleContext create(@NotNull Scale scale) {
      return new ScaleContext(scale);
    }

    private ScaleContext(@NotNull Scale scale) {
      setScale(scale);
    }

    public double getScale(@NotNull ScaleType type) {
      switch (type) {
        case USR_SCALE: return usrScale.value();
        case SYS_SCALE: return sysScale.value();
        case OBJ_SCALE: return objScale.value();
        case PIX_SCALE: return pixScale;
      }
      return 1f; // unreachable
    }

    // backward compatibility
    @SuppressWarnings("MethodOverloadsMethodOfSuperclass")
    public boolean update(@Nullable BaseScaleContext context) {
      return super.update(context);
    }
  }

  /**
   * @deprecated Use {@link JBScalableIcon}.
   */
  @Deprecated
  @SuppressWarnings("AbstractClassNeverImplemented")
  public abstract static class JBIcon extends JBScalableIcon {}
}