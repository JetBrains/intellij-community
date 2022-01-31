// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.diagnostic.LoadingState;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.scale.DerivedScaleType;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 * @author tav
 */
@SuppressWarnings("UseJBColor")
public final class JBUI {
  static {
    LoadingState.BASE_LAF_INITIALIZED.checkOccurred();
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

  /**
   * @deprecated use {@link JBUIScale#scale(float)}
   */
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

  public static @NotNull JBValue value(float value) {
    return new JBValue.Float(value);
  }

  public static @NotNull JBValue uiIntValue(@NotNull String key, int defValue) {
    return new JBValue.UIInteger(key, defValue);
  }

  public static @NotNull JBDimension size(int width, int height) {
    return new JBDimension(width, height);
  }

  public static @NotNull JBDimension size(int widthAndHeight) {
    return new JBDimension(widthAndHeight, widthAndHeight);
  }

  public static @NotNull JBDimension size(Dimension size) {
    return JBDimension.size(size);
  }

  public static @NotNull JBInsets insets(int top, int left, int bottom, int right) {
    return new JBInsets(top, left, bottom, right);
  }

  public static @NotNull JBInsets insets(int all) {
    return new JBInsets(all);
  }

  public static @NotNull JBInsets insets(@NonNls @NotNull String propName, @NotNull JBInsets defaultValue) {
    Insets i = UIManager.getInsets(propName);
    return i != null ? JBInsets.create(i) : defaultValue;
  }

  public static @NotNull JBInsets insets(int topBottom, int leftRight) {
    return JBInsets.create(topBottom, leftRight);
  }

  public static @NotNull JBInsets emptyInsets() {
    return JBInsets.emptyInsets();
  }

  public static @NotNull JBInsets insetsTop(int t) {
    return new JBInsets(t, 0, 0, 0);
  }

  public static @NotNull JBInsets insetsLeft(int l) {
    return new JBInsets(0, l, 0, 0);
  }

  public static @NotNull JBInsets insetsBottom(int b) {
    return new JBInsets(0, 0, b, 0);
  }

  public static @NotNull JBInsets insetsRight(int r) {
    return new JBInsets(0, 0, 0, r);
  }

  /**
   * @deprecated Use {@link JBUIScale#scaleIcon(JBScalableIcon)}.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static @NotNull <T extends JBScalableIcon> T scale(@NotNull T icon) {
    return JBUIScale.scaleIcon(icon);
  }

  public static @NotNull JBDimension emptySize() {
    return new JBDimension(0, 0);
  }

  public static @NotNull JBInsets insets(@NotNull Insets insets) {
    return JBInsets.create(insets);
  }

  /**
   * @deprecated use {@link JBUIScale#isUsrHiDPI()}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
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

  public static final class Fonts {
    public static @NotNull JBFont label() {
      return JBFont.label();
    }

    public static @NotNull JBFont label(float size) {
      return JBFont.label().deriveFont(JBUIScale.scale(size));
    }

    public static @NotNull JBFont smallFont() {
      return JBFont.label().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL));
    }

    public static @NotNull JBFont miniFont() {
      return JBFont.label().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.MINI));
    }

    public static @NotNull JBFont create(@NonNls String fontFamily, int size) {
      return JBFont.create(new Font(fontFamily, Font.PLAIN, size));
    }

    public static @NotNull JBFont toolbarFont() {
      return SystemInfoRt.isMac ? smallFont() : JBFont.label();
    }

    public static @NotNull JBFont toolbarSmallComboBoxFont() {
      return label(11);
    }
  }

  @SuppressWarnings("UseDPIAwareBorders")
  public static final class Borders {
    public static @NotNull JBEmptyBorder empty(int top, int left, int bottom, int right) {
      if (top == 0 && left == 0 && bottom == 0 && right == 0) {
        return JBEmptyBorder.SHARED_EMPTY_INSTANCE;
      }
      return new JBEmptyBorder(top, left, bottom, right);
    }

    public static @NotNull JBEmptyBorder empty(int topAndBottom, int leftAndRight) {
      return empty(topAndBottom, leftAndRight, topAndBottom, leftAndRight);
    }

    public static @NotNull JBEmptyBorder emptyTop(int offset) {
      return empty(offset, 0, 0, 0);
    }

    public static @NotNull JBEmptyBorder emptyLeft(int offset) {
      return empty(0, offset,  0, 0);
    }

    public static @NotNull JBEmptyBorder emptyBottom(int offset) {
      return empty(0, 0, offset, 0);
    }

    public static @NotNull JBEmptyBorder emptyRight(int offset) {
      return empty(0, 0, 0, offset);
    }

    public static @NotNull JBEmptyBorder empty() {
      return empty(0, 0, 0, 0);
    }

    public static @NotNull Border empty(int offsets) {
      return empty(offsets, offsets, offsets, offsets);
    }

    public static @NotNull Border empty(@NotNull Insets insets) {
      return empty(insets.top, insets.left, insets.bottom, insets.right);
    }

    public static @NotNull Border customLine(Color color, int top, int left, int bottom, int right) {
      return new CustomLineBorder(color, new JBInsets(top, left, bottom, right));
    }

    public static @NotNull Border customLine(Color color, int thickness) {
      return customLine(color, thickness, thickness, thickness, thickness);
    }

    public static @NotNull Border customLine(Color color) {
      return customLine(color, 1);
    }

    public static @NotNull Border customLineTop(Color color) {
      return customLine(color, 1, 0, 0, 0);
    }

    public static @NotNull Border customLineLeft(Color color) {
      return customLine(color, 0, 1, 0, 0);
    }

    public static @NotNull Border customLineRight(Color color) {
      return customLine(color, 0, 0, 0, 1);
    }

    public static @NotNull Border customLineBottom(Color color) {
      return customLine(color, 0, 0, 1, 0);
    }

    public static @Nullable Border compound(@Nullable Border outside, @Nullable Border inside) {
      return inside == null ? outside : outside == null ? inside : new CompoundBorder(outside, inside);
    }

    public static @NotNull Border merge(@Nullable Border source, @NotNull Border extra, boolean extraIsOutside) {
      if (source == null) return extra;
      return new CompoundBorder(extraIsOutside ? extra : source, extraIsOutside? source : extra);
    }
  }

  public static final class Panels {
    public static @NotNull BorderLayoutPanel simplePanel() {
      return new BorderLayoutPanel();
    }

    public static @NotNull BorderLayoutPanel simplePanel(Component comp) {
      return simplePanel().addToCenter(comp);
    }

    public static @NotNull BorderLayoutPanel simplePanel(int hgap, int vgap) {
      return new BorderLayoutPanel(hgap, vgap);
    }
  }

  public static Border asUIResource(@NotNull Border border) {
    if (border instanceof UIResource) return border;
    return new BorderUIResource(border);
  }

  @SuppressWarnings("UnregisteredNamedColor")
  public static final class CurrentTheme {
    public static final class Component {
      public static final Color FOCUSED_BORDER_COLOR = JBColor.namedColor("Component.focusedBorderColor", 0x87AFDA, 0x466D94);
    }

    public static final class ActionButton {
      public static @NotNull Color pressedBackground() {
        return JBColor.namedColor("ActionButton.pressedBackground", Gray.xCF);
      }

      public static @NotNull Color pressedBorder() {
        return JBColor.namedColor("ActionButton.pressedBorderColor", Gray.xCF);
      }

      public static @NotNull Color focusedBorder() {
        return JBColor.namedColor("ActionButton.focusedBorderColor", new JBColor(0x62b8de, 0x5eacd0));
      }

      public static @NotNull Color hoverBackground() {
        return JBColor.namedColor("ActionButton.hoverBackground", Gray.xDF);
      }

      public static @NotNull Color hoverBorder() {
        return JBColor.namedColor("ActionButton.hoverBorderColor", Gray.xDF);
      }

      public static @NotNull Color hoverSeparatorColor() {
        return JBColor.namedColor("ActionButton.hoverSeparatorColor", new JBColor(Gray.xB3, Gray.x6B));
      }
    }

    public static final class ActionsList {
      public static final Color MNEMONIC_FOREGROUND = JBColor.namedColor("Component.infoForeground", new JBColor(Gray.x99, Gray.x78));

      public static @NotNull Insets numberMnemonicInsets() {
        return insets("ActionsList.mnemonicsBorderInsets", insets(0, 8, 1, 6));
      }

      public static @NotNull Insets cellPadding() {
        return insets("ActionsList.cellBorderInsets", insets(1, 12, 1, 12));
      }

      public static int elementIconGap() {
        return new JBValue.UIInteger("ActionsList.icon.gap", scale(6)).get();
      }

      public static int mnemonicIconGap() {
        return new JBValue.UIInteger("ActionsList.mnemonic.icon.gap", scale(6)).get();
      }

      public static @NotNull Font applyStylesForNumberMnemonic(Font font) {
        if (SystemInfoRt.isWindows) {
          Map<TextAttribute, Object> attributes = new HashMap<>(font.getAttributes());
          attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
          return font.deriveFont(attributes);
        }
        return font;
      }
    }

    public static final class Button {
      public static @NotNull Color buttonColorStart() {
        return JBColor.namedColor("Button.startBackground", JBColor.namedColor("Button.darcula.startColor", 0x555a5c));
      }

      public static @NotNull Color buttonColorEnd() {
        return JBColor.namedColor("Button.endBackground", JBColor.namedColor("Button.darcula.endColor", 0x414648));
      }

      public static @NotNull Color defaultButtonColorStart() {
        return JBColor.namedColor("Button.default.startBackground", JBColor.namedColor("Button.darcula.defaultStartColor", 0x384f6b));
      }

      public static @NotNull Color defaultButtonColorEnd() {
        return JBColor.namedColor("Button.default.endBackground", JBColor.namedColor("Button.darcula.defaultEndColor", 0x233143));
      }

      public static @NotNull Color focusBorderColor(boolean isDefaultButton) {
        return isDefaultButton ?
               JBColor.namedColor("Button.default.focusedBorderColor", JBColor.namedColor("Button.darcula.defaultFocusedOutlineColor", 0x87afda)) :
               JBColor.namedColor("Button.focusedBorderColor", JBColor.namedColor("Button.darcula.focusedOutlineColor", 0x87afda));
      }

      public static @NotNull Color buttonOutlineColorStart(boolean isDefaultButton) {
        return isDefaultButton ?
               JBColor.namedColor("Button.default.startBorderColor", JBColor.namedColor("Button.darcula.outlineDefaultStartColor", Gray.xBF)) :
               JBColor.namedColor("Button.startBorderColor",  JBColor.namedColor("Button.darcula.outlineStartColor", Gray.xBF));
      }

      public static @NotNull Color buttonOutlineColorEnd(boolean isDefaultButton) {
        return isDefaultButton ?
               JBColor.namedColor("Button.default.endBorderColor", JBColor.namedColor("Button.darcula.outlineDefaultEndColor", Gray.xB8)) :
               JBColor.namedColor("Button.endBorderColor",  JBColor.namedColor("Button.darcula.outlineEndColor", Gray.xB8));
      }

      public static @NotNull Color disabledOutlineColor() {
        return JBColor.namedColor("Button.disabledBorderColor", JBColor.namedColor("Button.darcula.disabledOutlineColor", Gray.xCF));
      }
    }

    public interface SegmentedButton {
      Color SELECTED_START_BORDER_COLOR = JBColor.namedColor("SegmentedButton.selectedStartBorderColor", Gray.xBF);
      Color SELECTED_END_BORDER_COLOR = JBColor.namedColor("SegmentedButton.selectedEndBorderColor", Gray.xB8);

      Color SELECTED_BUTTON_COLOR = JBColor.namedColor("SegmentedButton.selectedButtonColor", 0x555a5c);
      Color FOCUSED_SELECTED_BUTTON_COLOR = JBColor.namedColor("SegmentedButton.focusedSelectedButtonColor", 0xDAE4ED);
    }

    public static final class CustomFrameDecorations {
      public static @NotNull Color separatorForeground() {
        return JBColor.namedColor("Separator.separatorColor", new JBColor(0xcdcdcd, 0x515151));
      }

      public static @NotNull Color titlePaneButtonHoverBackground() {
        return JBColor.namedColor("TitlePane.Button.hoverBackground",
                           new JBColor(ColorUtil.withAlpha(Color.BLACK, .1),
                                       ColorUtil.withAlpha(Color.WHITE, .1)));
      }

      public static @NotNull Color titlePaneButtonPressBackground() {
        return titlePaneButtonHoverBackground();
      }

      public static @NotNull Color titlePaneInactiveBackground() {
        return JBColor.namedColor("TitlePane.inactiveBackground", titlePaneBackground());
      }

      public static @NotNull Color titlePaneBackground(boolean active) {
        return active ? titlePaneBackground() : titlePaneInactiveBackground();
      }

      public static @NotNull Color titlePaneBackground() {
        return JBColor.namedColor("TitlePane.background", paneBackground());
      }

      public static @NotNull Color mainToolbarBackground(boolean active) {
        JBColor activeBG = JBColor.namedColor("MainToolbar.background", titlePaneBackground());
        JBColor inactiveBG = JBColor.namedColor("MainToolbar.inactiveBackground", activeBG);
        return active ? activeBG : inactiveBG;
      }

      public static @NotNull Color titlePaneInfoForeground() {
        return JBColor.namedColor("TitlePane.infoForeground", new JBColor(0x616161, 0x919191));
      }

      public static @NotNull Color titlePaneInactiveInfoForeground() {
        return JBColor.namedColor("TitlePane.inactiveInfoForeground", new JBColor(0xA6A6A6, 0x737373));
      }

      public static @NotNull Color paneBackground() {
        return JBColor.namedColor("Panel.background", Gray.xCD);
      }
    }

    public static final class DefaultTabs {
      public static @NotNull Color underlineColor() {
        return JBColor.namedColor("DefaultTabs.underlineColor", new JBColor(0x4083C9, 0x4A88C7));
      }

      public static int underlineHeight() {
        return getInt("DefaultTabs.underlineHeight", JBUIScale.scale(3));
      }

      public static @NotNull Color inactiveUnderlineColor() {
        return JBColor.namedColor("DefaultTabs.inactiveUnderlineColor", new JBColor(0x9ca7b8, 0x747a80));
      }

      public static @NotNull Color borderColor() {
        return JBColor.namedColor("DefaultTabs.borderColor", UIUtil.CONTRAST_BORDER_COLOR);
      }

      public static @NotNull Color background() {
        return JBColor.namedColor("DefaultTabs.background", new JBColor(0xECECEC, 0x3C3F41));
      }

      public static @NotNull Color hoverBackground() {
        return JBColor.namedColor("DefaultTabs.hoverBackground",
                                  new JBColor(ColorUtil.withAlpha(Color.BLACK, .10),
                                              ColorUtil.withAlpha(Color.BLACK, .35)));
      }

      public static Color underlinedTabBackground() {
        return UIManager.getColor("DefaultTabs.underlinedTabBackground");
      }

      public static @NotNull Color underlinedTabForeground() {
        return JBColor.namedColor("DefaultTabs.underlinedTabForeground", UIUtil.getLabelForeground());
      }

      public static @NotNull Color inactiveColoredTabBackground() {
        return JBColor.namedColor("DefaultTabs.inactiveColoredTabBackground", new JBColor(ColorUtil.withAlpha(Color.BLACK, .07),
                                                                                          ColorUtil.withAlpha(new Color(0x3C3F41), .60)));
      }
    }

    public static final class DebuggerTabs {
      public static int underlineHeight() {
        return getInt("DebuggerTabs.underlineHeight", JBUIScale.scale(2));
      }

      public static Color underlinedTabBackground() {
        return UIManager.getColor("DebuggerTabs.underlinedTabBackground");
      }
    }

    public static final class EditorTabs {
      public static @NotNull Color underlineColor() {
        return JBColor.namedColor("EditorTabs.underlineColor", DefaultTabs.underlineColor());
      }

      public static int underlineHeight() {
        return getInt("EditorTabs.underlineHeight", DefaultTabs.underlineHeight());
      }

      public static int underlineArc() {
        return getInt("EditorTabs.underlineArc", 0);
      }

      public static @NotNull Color inactiveUnderlineColor() {
        return JBColor.namedColor("EditorTabs.inactiveUnderlineColor", DefaultTabs.inactiveUnderlineColor());
      }

      public static Color underlinedTabBackground() {
        return UIManager.getColor("EditorTabs.underlinedTabBackground");
      }

      public static Insets tabInsets() {
        return insets("EditorTabs.tabInsets", insets(0, 8));
      }

      public static @NotNull Color borderColor() {
        return JBColor.namedColor("EditorTabs.borderColor", DefaultTabs.borderColor());
      }

      public static @NotNull Color background() {
        return JBColor.namedColor("EditorTabs.background", DefaultTabs.background());
      }

      public static @NotNull Color hoverBackground() {
        return JBColor.namedColor("EditorTabs.hoverBackground", DefaultTabs.hoverBackground());
      }

      public static @NotNull Color hoverBackground(boolean selected, boolean active) {
        String key = selected
                     ? active ? "EditorTabs.hoverSelectedBackground" : "EditorTabs.hoverSelectedInactiveBackground"
                     : active ? "EditorTabs.hoverBackground" : "EditorTabs.hoverInactiveBackground";

        return JBColor.namedColor(key, selected ? Gray.TRANSPARENT : DefaultTabs.hoverBackground());
      }

      public static @NotNull Color inactiveColoredFileBackground() {
        return JBColor.namedColor("EditorTabs.inactiveColoredFileBackground", DefaultTabs.inactiveColoredTabBackground());
      }

      public static @NotNull Color underlinedTabForeground() {
        return JBColor.namedColor("EditorTabs.underlinedTabForeground", DefaultTabs.underlinedTabForeground());
      }
    }

    public interface DragAndDrop {
      Color BORDER_COLOR = JBColor.namedColor("DragAndDrop.borderColor", 0x2675BF, 0x2F65CA);
      Color ROW_BACKGROUND = JBColor.namedColor("DragAndDrop.rowBackground", 0x2675BF26, 0x2F65CA33);

      interface Area {
        Color FOREGROUND = JBColor.namedColor("DragAndDrop.areaForeground", 0x787878, 0xBABABA);
        Color BACKGROUND = JBColor.namedColor("DragAndDrop.areaBackground", 0x3D7DCC, 0x404A57);
      }
    }

    public interface IconBadge {
      Color ERROR = JBColor.namedColor("IconBadge.errorBackground", 0xE35252, 0xDB5C5C);
      Color INFORMATION = JBColor.namedColor("IconBadge.infoBackground", 0x588CF3, 0x548AF7);
      Color SUCCESS = JBColor.namedColor("IconBadge.successBackground", 0x5FB865, 0x5FAD65);
    }

    public interface Notification {
      Color FOREGROUND = JBColor.namedColor("Notification.foreground", Label.foreground());
      Color BACKGROUND = JBColor.namedColor("Notification.background", 0xFFF8D1, 0x1D3857);

      interface Error {
        Color FOREGROUND = JBColor.namedColor("Notification.errorForeground", Notification.FOREGROUND);
        Color BACKGROUND = JBColor.namedColor("Notification.errorBackground", 0xF5E6E7, 0x593D41);
        Color BORDER_COLOR = JBColor.namedColor("Notification.errorBorderColor", 0xE0A8A9, 0x73454B);
      }
    }

    public static final class StatusBar {
      public static final Color BACKGROUND = JBColor.namedColor("StatusBar.background", JBColor.PanelBackground);
      public static final Color BORDER_COLOR = JBColor.namedColor("StatusBar.borderColor", Gray.x91);
      /**
       * @deprecated Use {@link Widget#HOVER_BACKGROUND} instead.
       */
      @Deprecated
      public static Color hoverBackground() {
        return Widget.HOVER_BACKGROUND;
      }

      public interface Widget {
        Color FOREGROUND = JBColor.namedColor("StatusBar.Widget.foreground", UIUtil.getLabelForeground());
        Color HOVER_FOREGROUND = JBColor.namedColor("StatusBar.Widget.hoverForeground", UIUtil.getLabelForeground());
        Color HOVER_BACKGROUND = JBColor.namedColor("StatusBar.Widget.hoverBackground", ActionButton.hoverBackground());
        Color PRESSED_FOREGROUND = JBColor.namedColor("StatusBar.Widget.pressedForeground", UIUtil.getLabelForeground());
        Color PRESSED_BACKGROUND = JBColor.namedColor("StatusBar.Widget.pressedBackground", ActionButton.pressedBackground());

        static Border iconBorder() {
          return new JBEmptyBorder(insets("StatusBar.Widget.widgetInsets", insets(0, 4)));
        }

        static Border border() {
          return new JBEmptyBorder(insets("StatusBar.Widget.widgetInsets", insets(0, 6)));
        }
      }

      public interface Breadcrumbs {
        Color FOREGROUND = JBColor.namedColor("StatusBar.Breadcrumbs.foreground", StatusBar.Widget.FOREGROUND);
        Color HOVER_FOREGROUND = JBColor.namedColor("StatusBar.Breadcrumbs.hoverForeground", UIUtil.getLabelForeground());
        Color HOVER_BACKGROUND = JBColor.namedColor("StatusBar.Breadcrumbs.hoverBackground", ActionButton.hoverBackground());
        Color SELECTION_FOREGROUND = JBColor.namedColor("StatusBar.Breadcrumbs.selectionForeground", List.Selection.foreground(true));
        Color SELECTION_BACKGROUND = JBColor.namedColor("StatusBar.Breadcrumbs.selectionBackground", List.Selection.background(true));
        Color SELECTION_INACTIVE_FOREGROUND = JBColor.namedColor("StatusBar.Breadcrumbs.selectionInactiveForeground", List.Selection.foreground(false));
        Color SELECTION_INACTIVE_BACKGROUND = JBColor.namedColor("StatusBar.Breadcrumbs.selectionInactiveBackground", List.Selection.background(false));

        Color FLOATING_BACKGROUND = JBColor.namedColor("StatusBar.Breadcrumbs.floatingBackground", List.BACKGROUND);
        Color FLOATING_FOREGROUND = JBColor.namedColor("StatusBar.Breadcrumbs.floatingForeground", UIUtil.getLabelForeground());
        JBValue CHEVRON_INSET = new JBValue.UIInteger("StatusBar.Breadcrumbs.chevronInset", 0);

        static Insets floatingBorderInsets() {
          return insets("StatusBar.Breadcrumbs.floatingToolbarInsets", JBInsets.emptyInsets());
        }
      }
    }

    public static final class ToolWindow {
      public static @NotNull Color background() {
        return JBColor.namedColor("ToolWindow.background");
      }

      public static @NotNull Color borderColor() {
        return JBColor.namedColor("ToolWindow.HeaderTab.borderColor", DefaultTabs.borderColor());
      }

      public static @NotNull Color underlinedTabForeground() {
        return JBColor.namedColor("ToolWindow.HeaderTab.underlinedTabForeground", DefaultTabs.underlinedTabForeground());
      }

      public static @NotNull Color hoverBackground() {
        return JBColor.namedColor("ToolWindow.HeaderTab.hoverBackground", DefaultTabs.hoverBackground());
      }

      public static @NotNull Color inactiveUnderlineColor() {
        return JBColor.namedColor("ToolWindow.HeaderTab.inactiveUnderlineColor", DefaultTabs.inactiveUnderlineColor());
      }

      public static @NotNull Color underlineColor() {
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

      public static @NotNull Color underlinedTabInactiveForeground() {
        return JBColor.namedColor("ToolWindow.HeaderTab.underlinedTabInactiveForeground", underlinedTabForeground());
      }

      public static @NotNull Insets headerTabInsets() {
        return insets("ToolWindow.HeaderTab.insets", insets(0, 12, 0, 12));
      }

      public static int headerTabUnderlineArc() {
        return getInt("ToolWindow.HeaderTab.underlineArc", 4);
      }

      public static JBInsets headerTabLeftRightInsets() {
        return insets("ToolWindow.HeaderTab.leftRightInsets", insets(0, 8, 0, 8));
      }

      public interface DragAndDrop {
        Color STRIPE_BACKGROUND = JBColor.namedColor("ToolWindow.Button.DragAndDrop.stripeBackground", CurrentTheme.DragAndDrop.Area.BACKGROUND);
        Color BUTTON_DROP_BACKGROUND = JBColor.namedColor("ToolWindow.Button.DragAndDrop.buttonDropBackground", CurrentTheme.DragAndDrop.Area.BACKGROUND);
        Color BUTTON_FLOATING_BACKGROUND = JBColor.namedColor("ToolWindow.Button.DragAndDrop.buttonFloatingBackground", ActionButton.pressedBackground());
      }

      public static @NotNull Color headerBackground(boolean active) {
        return active ? headerActiveBackground() : headerBackground();
      }

      public static @NotNull Color headerBackground() {
        return JBColor.namedColor("ToolWindow.Header.inactiveBackground", JBColor.namedColor("ToolWindow.header.background", 0xECECEC));
      }

      public static @NotNull Color headerBorderBackground() {
        return JBColor.namedColor("ToolWindow.Header.borderColor", DefaultTabs.borderColor());
      }

      public static @NotNull Color headerActiveBackground() {
        return JBColor.namedColor("ToolWindow.Header.background", JBColor.namedColor("ToolWindow.header.active.background", 0xE2E6EC));
      }

      public static @NotNull Insets headerInsets() {
        return insets("ToolWindow.Header.insets", insets(4, 8, 4, 8));
      }

      public static int headerHeight() {
        return getInt("ToolWindow.Header.height", 42);
      }

      public static JBInsets headerLabelLeftRightInsets() {
        return insets("ToolWindow.Header.labelLeftRightInsets", insets(0, 12, 0, 16));
      }

      public static JBInsets headerToolbarLeftRightInsets() {
        return insets("ToolWindow.Header.toolbarLeftRightInsets", insets(0, 12, 0, 8));
      }

      /**
       * @deprecated obsolete UI
       */
      @Deprecated
      @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
      public static int tabVerticalPadding() {
        return getInt("ToolWindow.HeaderTab.verticalPadding", JBUIScale.scale(6));
      }

      public static int underlineHeight() {
        return getInt("ToolWindow.HeaderTab.underlineHeight", DefaultTabs.underlineHeight());
      }


      public static @NotNull Font headerFont() {
        return JBFont.label();
      }

      public static float overrideHeaderFontSizeOffset() {
        Object offset = UIManager.get("ToolWindow.Header.font.size.offset");
        if (offset instanceof Integer) {
          return ((Integer)offset).floatValue();
        }

        return 0;
      }

      public static @NotNull Color hoveredIconBackground() {
        return JBColor.namedColor("ToolWindow.HeaderCloseButton.background", JBColor.namedColor("ToolWindow.header.closeButton.background", 0xB9B9B9));
      }

      public static @NotNull Icon closeTabIcon(boolean hovered) {
        return hovered ? getIcon("ToolWindow.header.closeButton.hovered.icon", AllIcons.Actions.CloseHovered)
                       : getIcon("ToolWindow.header.closeButton.icon", AllIcons.Actions.Close);
      }

      public static @NotNull Icon comboTabIcon(boolean hovered) {
        return hovered ? getIcon("ToolWindow.header.comboButton.hovered.icon", AllIcons.General.ArrowDown)
                       : getIcon("ToolWindow.header.comboButton.icon", AllIcons.General.ArrowDown);
      }
    }

    public static final class Label {
      public static @NotNull Color foreground(boolean selected) {
        return selected ? JBColor.namedColor("Label.selectedForeground", 0xFFFFFF)
                        : JBColor.namedColor("Label.foreground", 0x000000);
      }

      public static @NotNull Color foreground() {
        return foreground(false);
      }

      public static @NotNull Color disabledForeground(boolean selected) {
        return selected ? JBColor.namedColor("Label.selectedDisabledForeground", 0x999999)
                        : JBColor.namedColor("Label.disabledForeground", JBColor.namedColor("Label.disabledText", 0x999999));
      }

      public static @NotNull Color disabledForeground() {
        return disabledForeground(false);
      }
    }

    public static final class Popup {
      public static int bodyBottomInsetNoAd() {
        return getInt("Popup.Body.bottomInsetNoAd", 8);
      }

      public static int bodyBottomInsetBeforeAd() {
        return getInt("Popup.Body.bottomInsetBeforeAd", 8);
      }

      public static int bodyTopInsetNoHeader() {
        return getInt("Popup.Body.topInsetNoHeader", 8);
      }

      public static Color headerBackground(boolean active) {
        return active
               ? JBColor.namedColor("Popup.Header.activeBackground", 0xe6e6e6)
               : JBColor.namedColor("Popup.Header.inactiveBackground", 0xededed);
      }

      public static Color headerForeground(boolean active) {
        return active
               ? JBColor.namedColor("Popup.Header.activeForeground", UIUtil.getLabelForeground())
               : JBColor.namedColor("Popup.Header.inactiveForeground", UIUtil.getLabelDisabledForeground());
      }

      @NotNull
      public static Border headerInsets() {
        return new JBEmptyBorder(insets("Popup.Header.insets", insets(12, 10, 10, 10)));
      }

      public static int headerHeight(boolean hasControls) {
        return hasControls ? JBUIScale.scale(28) : JBUIScale.scale(24);
      }

      public static Color borderColor(boolean active) {
        return active
               ? JBColor.namedColor("Popup.borderColor", JBColor.namedColor("Popup.Border.color", 0x808080))
               : JBColor.namedColor("Popup.inactiveBorderColor", JBColor.namedColor("Popup.inactiveBorderColor", 0xaaaaaa));
      }

      public static float borderWidth() {
        return getFloat("Popup.borderWidth", 1);
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

      public static Insets separatorInsets() {
        return insets("Popup.separatorInsets", insets(4, 12, 4, 12));
      }

      public static Insets separatorLabelInsets() {
        return insets("Popup.separatorLabelInsets", insets(2, 20, 2, 20));
      }

      public static Color separatorTextColor() {
        return JBColor.namedColor("Popup.separatorForeground", Color.gray);
      }

      public static int minimumHintWidth() {
        return JBUIScale.scale(170);
      }
    }

    public static final class Focus {
      private static final Color GRAPHITE_COLOR = new JBColor(new Color(0x8099979d, true), new Color(0x676869));

      public static @NotNull Color focusColor() {
        return UIUtil.isGraphite() ? GRAPHITE_COLOR : JBColor.namedColor("Component.focusColor", JBColor.namedColor("Focus.borderColor", 0x8ab2eb));
      }

      public static @NotNull Color defaultButtonColor() {
        return StartupUiUtil.isUnderDarcula() ? JBColor.namedColor("Button.default.focusColor",
                                    JBColor.namedColor("Focus.defaultButtonBorderColor", 0x97c3f3)) : focusColor();
      }

      public static @NotNull Color errorColor(boolean active) {
        return active ? JBColor.namedColor("Component.errorFocusColor", JBColor.namedColor("Focus.activeErrorBorderColor", 0xe53e4d)) :
                        JBColor.namedColor("Component.inactiveErrorFocusColor", JBColor.namedColor("Focus.inactiveErrorBorderColor", 0xebbcbc));
      }

      public static @NotNull Color warningColor(boolean active) {
        return active ? JBColor.namedColor("Component.warningFocusColor", JBColor.namedColor("Focus.activeWarningBorderColor", 0xe2a53a)) :
                        JBColor.namedColor("Component.inactiveWarningFocusColor", JBColor.namedColor("Focus.inactiveWarningBorderColor", 0xffd385));
      }
    }

    public static final class TabbedPane {
      public static final Color ENABLED_SELECTED_COLOR = JBColor.namedColor("TabbedPane.underlineColor", JBColor.namedColor("TabbedPane.selectedColor", 0x4083C9));
      public static final Color DISABLED_SELECTED_COLOR = JBColor.namedColor("TabbedPane.disabledUnderlineColor", JBColor.namedColor("TabbedPane.selectedDisabledColor", Gray.xAB));
      public static final Color DISABLED_TEXT_COLOR = JBColor.namedColor("TabbedPane.disabledForeground", JBColor.namedColor("TabbedPane.disabledText", Gray.x99));
      public static final Color HOVER_COLOR = JBColor.namedColor("TabbedPane.hoverColor", Gray.xD9);
      public static final Color FOCUS_COLOR = JBColor.namedColor("TabbedPane.focusColor", 0xDAE4ED);
      public static final JBValue TAB_HEIGHT = new JBValue.UIInteger("TabbedPane.tabHeight", 32);
      public static final JBValue SELECTION_HEIGHT = new JBValue.UIInteger("TabbedPane.tabSelectionHeight", 3);
    }

    public static final class BigPopup {
      public static @NotNull Color headerBackground() {
        return JBColor.namedColor("SearchEverywhere.Header.background", 0xf2f2f2);
      }

      public static @NotNull Insets tabInsets() {
        return JBInsets.create(0, 12);
      }

      public static @NotNull Color selectedTabColor() {
        return JBColor.namedColor("SearchEverywhere.Tab.selectedBackground", 0xdedede);
      }

      public static @NotNull Color selectedTabTextColor() {
        return JBColor.namedColor("SearchEverywhere.Tab.selectedForeground", 0x000000);
      }

      public static @NotNull Color searchFieldBackground() {
        return JBColor.namedColor("SearchEverywhere.SearchField.background", 0xffffff);
      }

      public static @NotNull Color searchFieldBorderColor() {
        return JBColor.namedColor("SearchEverywhere.SearchField.borderColor", 0xbdbdbd);
      }

      public static @NotNull Insets searchFieldInsets() {
        return insets(0, 6, 0, 5);
      }

      public static int maxListHeight() {
        return JBUIScale.scale(600);
      }

      public static @NotNull Color listSeparatorColor() {
        return JBColor.namedColor("SearchEverywhere.List.separatorColor", Gray.xDC);
      }

      public static @NotNull Color listTitleLabelForeground() {
        return JBColor.namedColor("SearchEverywhere.List.separatorForeground", UIUtil.getLabelDisabledForeground());
      }

      public static @NotNull Color searchFieldGrayForeground()  {
        return JBColor.namedColor("SearchEverywhere.SearchField.infoForeground", JBColor.GRAY);
      }

      public static @NotNull Color advertiserForeground()  {
        return JBColor.namedColor("SearchEverywhere.Advertiser.foreground", JBColor.GRAY);
      }

      public static @NotNull Border advertiserBorder()  {
        return new JBEmptyBorder(insets("SearchEverywhere.Advertiser.borderInsets", insets(5, 10, 5, 15)));
      }

      public static @NotNull Color advertiserBackground()  {
        return JBColor.namedColor("SearchEverywhere.Advertiser.background", 0xf2f2f2);
      }
    }

    public static final class Advertiser {
      public static @NotNull Color foreground() {
        Color foreground = JBUI.CurrentTheme.BigPopup.advertiserForeground();
        return JBColor.namedColor("Popup.Advertiser.foreground", foreground);
      }

      public static @NotNull Color background() {
        Color background = JBUI.CurrentTheme.BigPopup.advertiserBackground();
        return JBColor.namedColor("Popup.Advertiser.background", background);
      }

      public static @NotNull Border border() {
        return new JBEmptyBorder(insets("Popup.Advertiser.borderInsets", insets(5, 10, 5, 15)));
      }

      public static @NotNull Color borderColor() {
        return JBColor.namedColor("Popup.Advertiser.borderColor", Gray._135);
      }
    }

    public static final class Validator {
      public static @NotNull Color errorBorderColor() {
        return JBColor.namedColor("ValidationTooltip.errorBorderColor", 0xE0A8A9);
      }

      public static @NotNull Color errorBackgroundColor() {
        return JBColor.namedColor("ValidationTooltip.errorBackground", JBColor.namedColor("ValidationTooltip.errorBackgroundColor", 0xF5E6E7));
      }

      public static @NotNull Color warningBorderColor() {
        return JBColor.namedColor("ValidationTooltip.warningBorderColor", 0xE0CEA8);
      }

      public static @NotNull Color warningBackgroundColor() {
        return JBColor.namedColor("ValidationTooltip.warningBackground", JBColor.namedColor("ValidationTooltip.warningBackgroundColor", 0xF5F0E6));
      }
    }

    public static final class Link {
      public static final @NotNull Color FOCUSED_BORDER_COLOR = JBColor.namedColor("Link.focusedBorderColor", Component.FOCUSED_BORDER_COLOR);

      public interface Foreground {
        @NotNull Color DISABLED = JBColor.namedColor("Link.disabledForeground", Label.disabledForeground());
        @NotNull Color ENABLED = JBColor.namedColor("Link.activeForeground", JBColor.namedColor("link.foreground", 0x589DF6));
        @NotNull Color HOVERED = JBColor.namedColor("Link.hoverForeground", JBColor.namedColor("link.hover.foreground", ENABLED));
        @NotNull Color PRESSED = JBColor.namedColor("Link.pressedForeground", JBColor.namedColor("link.pressed.foreground", 0xF00000, 0xBA6F25));
        @NotNull Color VISITED = JBColor.namedColor("Link.visitedForeground", JBColor.namedColor("link.visited.foreground", 0x800080, 0x9776A9));
        @NotNull Color SECONDARY = JBColor.namedColor("Link.secondaryForeground", 0x779DBD, 0x5676A0);
      }

      /**
       * @deprecated use {@link Foreground#ENABLED} instead
       */
      @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
      @Deprecated
      public static @NotNull Color linkColor() {
        return Foreground.ENABLED;
      }

      /**
       * @deprecated use {@link Foreground#HOVERED} instead
       */
      @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
      @Deprecated
      public static @NotNull Color linkHoverColor() {
        return Foreground.HOVERED;
      }

      /**
       * @deprecated use {@link Foreground#PRESSED} instead
       */
      @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
      @Deprecated
      public static @NotNull Color linkPressedColor() {
        return Foreground.PRESSED;
      }

      /**
       * @deprecated use {@link Foreground#VISITED} instead
       */
      @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
      @Deprecated
      public static @NotNull Color linkVisitedColor() {
        return Foreground.VISITED;
      }
    }

    public static final class Tooltip {
      public static @NotNull Color shortcutForeground () {
        return JBColor.namedColor("ToolTip.shortcutForeground", new JBColor(0x787878, 0x999999));
      }

      public static @NotNull Color borderColor() {
        return JBColor.namedColor("ToolTip.borderColor", new JBColor(0xadadad, 0x636569));
      }
    }

    public static final class GotItTooltip {
      public static @NotNull Color foreground(boolean useContrastColors) {
        if (useContrastColors) {
          return JBColor.namedColor("Tooltip.Learning.foreground", 0xF5F5F5);
        } else {
          return JBColor.namedColor("GotItTooltip.foreground", UIUtil.getToolTipForeground());
        }
      }

      public static @NotNull Color background(boolean useContrastColors) {
        if (useContrastColors) {
          return JBColor.namedColor("Tooltip.Learning.background");
        } else {
          return JBColor.namedColor("GotItTooltip.background", UIUtil.getToolTipBackground());
        }
      }

      public static @NotNull Color shortcutForeground(boolean useContrastColors) {
        if (useContrastColors) {
          return JBColor.namedColor("Tooltip.Learning.spanForeground", 0xF5F5F5);
        } else {
          return JBColor.namedColor("GotItTooltip.shortcutForeground", Tooltip.shortcutForeground());
        }
      }

      public static @NotNull Color linkForeground() {
        return JBColor.namedColor("GotItTooltip.linkForeground", JBUI.CurrentTheme.Link.Foreground.ENABLED);
      }

      public static @NotNull Color borderColor(boolean useContrastColors) {
        if (useContrastColors) {
          return JBColor.namedColor("Tooltip.Learning.background", 0x1071E8, 0x0E62CF);
        } else {
          return JBColor.namedColor("GotItTooltip.borderColor", JBUI.CurrentTheme.Tooltip.borderColor());
        }
      }

      public static @NotNull Color buttonBackgroundContrast() {
        return JBColor.namedColor("Tooltip.Learning.spanBackground", 0x0D5CBD, 0x0250B0);
      }

      public static @NotNull Color buttonForegroundContrast() {
        return JBColor.namedColor("Tooltip.Learning.spanForeground", 0xF5F5F5);
      }
    }

    public interface ContextHelp {
      @NotNull Color FOREGROUND = JBColor.namedColor("Label.infoForeground", new JBColor(Gray.x78, Gray.x8C));
    }

    public static final class Arrow {
      public static @NotNull Color foregroundColor(boolean enabled) {
        return enabled ?
               JBColor.namedColor("ComboBox.ArrowButton.iconColor", JBColor.namedColor("ComboBox.darcula.arrowButtonForeground", Gray.x66)) :
               JBColor.namedColor("ComboBox.ArrowButton.disabledIconColor", JBColor.namedColor("ComboBox.darcula.arrowButtonDisabledForeground", Gray.xAB));

      }

      public static @NotNull Color backgroundColor(boolean enabled, boolean editable) {
        return enabled ?
               editable ? JBColor.namedColor("ComboBox.ArrowButton.background", JBColor.namedColor("ComboBox.darcula.editable.arrowButtonBackground", Gray.xFC)) :
               JBColor.namedColor("ComboBox.ArrowButton.nonEditableBackground", JBColor.namedColor("ComboBox.darcula.arrowButtonBackground", Gray.xFC))
                       : UIUtil.getPanelBackground();
      }
    }

    public static final class NewClassDialog {
      public static @NotNull Color searchFieldBackground() {
        return JBColor.namedColor("NewClass.SearchField.background", 0xffffff);
      }

      public static @NotNull Color panelBackground() {
        return JBColor.namedColor("NewClass.Panel.background", 0xf2f2f2);
      }

      public static @NotNull Color bordersColor() {
        return JBColor.namedColor(
          "TextField.borderColor",
          JBColor.namedColor("Component.borderColor", new JBColor(0xbdbdbd, 0x646464))
        );
      }

      public static int fieldsSeparatorWidth() {
        return getInt("NewClass.separatorWidth", JBUIScale.scale(10));
      }
    }

    public static final class NotificationError {
      public static @NotNull Color backgroundColor() {
        return JBColor.namedColor("Notification.ToolWindow.errorBackground", new JBColor(0xffcccc, 0x704745));
      }

      public static @NotNull Color foregroundColor() {
        return JBColor.namedColor("Notification.ToolWindow.errorForeground", UIUtil.getToolTipForeground());
      }

      public static @NotNull Color borderColor() {
        return JBColor.namedColor("Notification.ToolWindow.errorBorderColor", new JBColor(0xd69696, 0x998a8a));
      }
    }

    public static final class NotificationInfo {
      public static @NotNull Color backgroundColor() {
        return JBColor.namedColor("Notification.ToolWindow.informativeBackground", new JBColor(0xbaeeba, 0x33412E));
      }

      public static @NotNull Color foregroundColor() {
        return JBColor.namedColor("Notification.ToolWindow.informativeForeground", UIUtil.getToolTipForeground());
      }

      public static @NotNull Color borderColor() {
        return JBColor.namedColor("Notification.ToolWindow.informativeBorderColor", new JBColor(0xa0bf9d, 0x85997a));
      }
    }
        public static final class NotificationWarning {
      public static @NotNull Color backgroundColor() {
        return JBColor.namedColor("Notification.ToolWindow.warningBackground", new JBColor(0xf9f78e, 0x5a5221));
      }

      public static @NotNull Color foregroundColor() {
        return JBColor.namedColor("Notification.ToolWindow.warningForeground", UIUtil.getToolTipForeground());
      }

      public static @NotNull Color borderColor() {
        return JBColor.namedColor("Notification.ToolWindow.warningBorderColor", new JBColor(0xbab824, 0xa69f63));
      }
    }

    private static final Color DEFAULT_RENDERER_BACKGROUND = new JBColor(0xFFFFFF, 0x3C3F41);
    private static final Color DEFAULT_RENDERER_SELECTION_BACKGROUND = new JBColor(0x3875D6, 0x2F65CA);
    private static final Color DEFAULT_RENDERER_SELECTION_INACTIVE_BACKGROUND = new JBColor(0xD4D4D4, 0x0D293E);
    private static final Color DEFAULT_RENDERER_HOVER_BACKGROUND = new JBColor(0xEDF5FC, 0x464A4D);
    private static final Color DEFAULT_RENDERER_HOVER_INACTIVE_BACKGROUND = new JBColor(0xF5F5F5, 0x464A4D);

    public interface List {
      Color BACKGROUND = JBColor.namedColor("List.background", DEFAULT_RENDERER_BACKGROUND);
      Color FOREGROUND = JBColor.namedColor("List.foreground", Label.foreground(false));

      static @NotNull Color background(boolean selected, boolean focused) {
        return selected ? Selection.background(focused) : BACKGROUND;
      }

      static @NotNull Color foreground(boolean selected, boolean focused) {
        return selected ? Selection.foreground(focused) : FOREGROUND;
      }

      final class Selection {
        private static final Color BACKGROUND = JBColor.namedColor("List.selectionBackground", DEFAULT_RENDERER_SELECTION_BACKGROUND);
        private static final Color FOREGROUND = JBColor.namedColor("List.selectionForeground", Label.foreground(true));

        public static @NotNull Color background(boolean focused) {
          //todo[kb] remove?
          //if (focused && UIUtil.isUnderDefaultMacTheme()) {
          //  double alpha = getInt("List.selectedItemAlpha", 75);
          //  if (0 <= alpha && alpha < 100) return ColorUtil.mix(Color.WHITE, BACKGROUND, alpha / 100.0);
          //}
          return focused ? BACKGROUND : Inactive.BACKGROUND;
        }

        public static @NotNull Color foreground(boolean focused) {
          return focused ? FOREGROUND : Inactive.FOREGROUND;
        }

        private interface Inactive {
          Color BACKGROUND = JBColor.namedColor("List.selectionInactiveBackground", DEFAULT_RENDERER_SELECTION_INACTIVE_BACKGROUND);
          Color FOREGROUND = JBColor.namedColor("List.selectionInactiveForeground", List.FOREGROUND);
        }
      }

      final class Hover {
        private static final Color BACKGROUND = JBColor.namedColor("List.hoverBackground", DEFAULT_RENDERER_HOVER_BACKGROUND);

        public static @NotNull Color background(boolean focused) {
          return focused ? BACKGROUND : Inactive.BACKGROUND;
        }

        private interface Inactive {
          Color BACKGROUND = JBColor.namedColor("List.hoverInactiveBackground", DEFAULT_RENDERER_HOVER_INACTIVE_BACKGROUND);
        }
      }
    }

    public interface Table {
      Color BACKGROUND = JBColor.namedColor("Table.background", DEFAULT_RENDERER_BACKGROUND);
      Color FOREGROUND = JBColor.namedColor("Table.foreground", Label.foreground(false));

      static @NotNull Color background(boolean selected, boolean focused) {
        return selected ? Selection.background(focused) : BACKGROUND;
      }

      static @NotNull Color foreground(boolean selected, boolean focused) {
        return selected ? Selection.foreground(focused) : FOREGROUND;
      }

      final class Selection {
        private static final Color BACKGROUND = JBColor.namedColor("Table.selectionBackground", DEFAULT_RENDERER_SELECTION_BACKGROUND);
        private static final Color FOREGROUND = JBColor.namedColor("Table.selectionForeground", Label.foreground(true));

        public static @NotNull Color background(boolean focused) {
          return focused ? BACKGROUND : Inactive.BACKGROUND;
        }

        public static @NotNull Color foreground(boolean focused) {
          return focused ? FOREGROUND : Inactive.FOREGROUND;
        }

        private interface Inactive {
          Color BACKGROUND = JBColor.namedColor("Table.selectionInactiveBackground", DEFAULT_RENDERER_SELECTION_INACTIVE_BACKGROUND);
          Color FOREGROUND = JBColor.namedColor("Table.selectionInactiveForeground", Table.FOREGROUND);
        }
      }

      final class Hover {
        private static final Color BACKGROUND = JBColor.namedColor("Table.hoverBackground", DEFAULT_RENDERER_HOVER_BACKGROUND);

        public static @NotNull Color background(boolean focused) {
          return focused ? BACKGROUND : Inactive.BACKGROUND;
        }

        private interface Inactive {
          Color BACKGROUND = JBColor.namedColor("Table.hoverInactiveBackground", DEFAULT_RENDERER_HOVER_INACTIVE_BACKGROUND);
        }
      }
    }

    public interface Tree {
      Color BACKGROUND = JBColor.namedColor("Tree.background", DEFAULT_RENDERER_BACKGROUND);
      Color FOREGROUND = JBColor.namedColor("Tree.foreground", Label.foreground(false));

      static @NotNull Color background(boolean selected, boolean focused) {
        return selected ? Selection.background(focused) : BACKGROUND;
      }

      static @NotNull Color foreground(boolean selected, boolean focused) {
        return selected ? Selection.foreground(focused) : FOREGROUND;
      }

      final class Selection {
        private static final Color BACKGROUND = JBColor.namedColor("Tree.selectionBackground", DEFAULT_RENDERER_SELECTION_BACKGROUND);
        private static final Color FOREGROUND = JBColor.namedColor("Tree.selectionForeground", Label.foreground(true));

        public static @NotNull Color background(boolean focused) {
          return focused ? BACKGROUND : Inactive.BACKGROUND;
        }

        public static @NotNull Color foreground(boolean focused) {
          return focused ? FOREGROUND : Inactive.FOREGROUND;
        }

        private interface Inactive {
          Color BACKGROUND = JBColor.namedColor("Tree.selectionInactiveBackground", DEFAULT_RENDERER_SELECTION_INACTIVE_BACKGROUND);
          Color FOREGROUND = JBColor.namedColor("Tree.selectionInactiveForeground", Tree.FOREGROUND);
        }
      }

      final class Hover {
        private static final Color BACKGROUND = JBColor.namedColor("Tree.hoverBackground", DEFAULT_RENDERER_HOVER_BACKGROUND);

        public static @NotNull Color background(boolean focused) {
          return focused ? BACKGROUND : Inactive.BACKGROUND;
        }

        private interface Inactive {
          Color BACKGROUND = JBColor.namedColor("Tree.hoverInactiveBackground", DEFAULT_RENDERER_HOVER_INACTIVE_BACKGROUND);
        }
      }
    }
  }

  public static int getInt(@NonNls @NotNull String propertyName, int defaultValue) {
    Object value = UIManager.get(propertyName);
    return value instanceof Integer ? (Integer)value : defaultValue;
  }

  public static float getFloat(@NonNls @NotNull String propertyName, float defaultValue) {
    Object value = UIManager.get(propertyName);
    if (value instanceof Float) return (Float)value;
    if (value instanceof Double) return ((Double)value).floatValue();
    if (value instanceof String) {
      try {
        return Float.parseFloat((String)value);
      } catch (NumberFormatException ignore) {}
    }
    return defaultValue;
  }

  private static @NotNull Icon getIcon(@NonNls @NotNull String propertyName, @NotNull Icon defaultIcon) {
    Icon icon = UIManager.getIcon(propertyName);
    return icon == null ? defaultIcon : icon;
  }
}