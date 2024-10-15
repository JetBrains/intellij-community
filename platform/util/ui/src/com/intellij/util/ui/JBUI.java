// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.border.NamedBorderKt;
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
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author Konstantin Bulenkov
 * @author tav
 */
@SuppressWarnings("UseJBColor")
public final class JBUI {
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
    return JBUIScale.pixScale(gc);
  }

  /**
   * Returns the pixel scale factor, corresponding to the device the provided component is tied to.
   * In the IDE-managed HiDPI mode defaults to {@link #pixScale()}
   */
  public static float pixScale(@Nullable Component component) {
    return pixScale(component == null ? null : component.getGraphicsConfiguration());
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

  /**
   * @return 'i' unscaled by the user scale factor
   */
  public static int unscale(int i) {
    return Math.round(i / JBUIScale.scale(1f));
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

  public static @NotNull JBDimension size(@NonNls @NotNull String propName, @NotNull JBDimension defaultValue) {
    Dimension d = UIManager.getDimension(propName);
    return d != null ? JBDimension.size(d) : defaultValue;
  }

  public static @NotNull JBInsets insets(int top, int left, int bottom, int right) {
    return new JBInsets(top, left, bottom, right);
  }

  public static @NotNull JBInsets insets(int all) {
    return new JBInsets(all);
  }

  public static @NotNull JBInsets insets(@NonNls @NotNull String propName, @NotNull JBInsets defaultValue) {
    return JBInsets.create(propName, defaultValue);
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
  @Deprecated(forRemoval = true)
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
  @Deprecated(forRemoval = true)
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
      if (JBInsets.isZero(insets)) {
        return JBEmptyBorder.SHARED_EMPTY_INSTANCE;
      }
      return new JBEmptyBorder(insets);
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

    /**
     * @param borders list of border to be compound from outside to inside
     */
    public static @Nullable Border compound(Border @NotNull ... borders) {
      Border result = null;
      for (Border border : borders) {
        if (border != null) {
          if (result == null) {
            result = border;
          } else {
            result = new CompoundBorder(result, border);
          }
        }
      }
      return result;
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
      public static final JBValue ARROW_AREA_WIDTH = new JBValue.UIInteger("Component.arrowAreaWidth", 23);
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

      public static final Color SEPARATOR_COLOR = JBColor.namedColor("ActionButton.separatorColor", CustomFrameDecorations.separatorForeground());
    }

    public static final class ActionsList {
      public static final Color MNEMONIC_FOREGROUND = JBColor.namedColor("Component.infoForeground", new JBColor(Gray.x99, Gray.x78));

      public static @NotNull Insets numberMnemonicInsets() {
        return insets("ActionsList.mnemonicsBorderInsets", insets(0, 8, 1, 6));
      }

      public static @NotNull Insets mnemonicInsets() {
        return insets("ActionsList.mnemonicsInsets", insetsRight(8));
      }

      public static @NotNull Insets cellPadding() {
        return insets("ActionsList.cellBorderInsets", insets(1, 12));
      }

      public static int elementIconGap() {
        return new JBValue.UIInteger("ActionsList.icon.gap", 6).get();
      }

      public static int mnemonicIconGap() {
        return new JBValue.UIInteger("ActionsList.mnemonic.icon.gap", 6).get();
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

    public static final class Banner {
      public static final Color INFO_BACKGROUND = JBColor.namedColor("Banner.infoBackground", 0xF5F8FE, 0x25324D);
      public static final Color INFO_BORDER_COLOR = JBColor.namedColor("Banner.infoBorderColor", 0xC2D6FC, 0x35538F);

      public static final Color SUCCESS_BACKGROUND = JBColor.namedColor("Banner.successBackground", 0xF2FCF3, 0x253627);
      public static final Color SUCCESS_BORDER_COLOR = JBColor.namedColor("Banner.successBorderColor", 0xC5E5CC, 0x375239);

      public static final Color WARNING_BACKGROUND = JBColor.namedColor("Banner.warningBackground", 0xFFFAEB, 0x3d3223);
      public static final Color WARNING_BORDER_COLOR = JBColor.namedColor("Banner.warningBorderColor", 0xFED277, 0x5E4D33);

      public static final Color ERROR_BACKGROUND = JBColor.namedColor("Banner.errorBackground", 0xFFF7F7, 0x402929);
      public static final Color ERROR_BORDER_COLOR = JBColor.namedColor("Banner.errorBorderColor", 0xFAD4D8, 0x5E3838);

      public static final Color FOREGROUND = JBColor.namedColor("Banner.foreground", 0x0, 0xDFE1E5);
    }

    public static final class Button {
      public static @NotNull Color buttonColorStart() {
        return JBColor.namedColor("Button.startBackground", JBColor.namedColor("Button.darcula.startColor", 0x555a5c));
      }

      public static @NotNull Color buttonColorEnd() {
        return JBColor.namedColor("Button.endBackground", JBColor.namedColor("Button.darcula.endColor", 0x414648));
      }

      public static @NotNull Color buttonForeground() {
        return JBColor.namedColor("Button.foreground", 0x000000, 0xDFE1E5);
      }

      public static @NotNull Color buttonLoadingForeground() {
        return JBColor.namedColor("Button.loadingForeground", 0x818594, 0x868A91);
      }

      public static @NotNull Color defaultButtonColorStart() {
        return JBColor.namedColor("Button.default.startBackground", JBColor.namedColor("Button.darcula.defaultStartColor", 0x384f6b));
      }

      public static @NotNull Color defaultButtonColorEnd() {
        return JBColor.namedColor("Button.default.endBackground", JBColor.namedColor("Button.darcula.defaultEndColor", 0x233143));
      }

      public static @NotNull Color defaultButtonForeground() {
        return JBColor.namedColor("Button.default.foreground", 0xFFFFFF, 0xFFFFFF);
      }

      public static @NotNull Color defaultButtonLoadingForeground() {
        return JBColor.namedColor("Button.default.loadingForeground", 0xD3D5DB, 0xCED0D6);
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

      public static @NotNull Dimension minimumSize() {
        return size("Button.minimumSize", size(72, 24));
      }

      public static final class Split {
        public static final class Default {
          public static final @NotNull Color SEPARATOR_COLOR =
            JBColor.namedColor("Button.Split.default.separatorColor", 0x88ADF7, 0x83ACFC);

          public static final @NotNull Color ICON_COLOR = JBColor.namedColor("Button.Split.default.iconColor", 0xFFFFFF);
        }
      }
    }

    public static final class ComboBox {
      public static @NotNull Dimension minimumSize() {
        return size("ComboBox.minimumSize", size(49, 24));
      }
    }

    public interface SearchOption {
      JBColor BUTTON_SELECTED_BACKGROUND = JBColor.namedColor("SearchOption.selectedBackground", 0xDAE4ED, 0x5C6164);
      JBColor BUTTON_SELECTED_PRESSED_BACKGROUND =
        JBColor.namedColor("SearchOption.selectedPressedBackground", JBUI.CurrentTheme.ActionButton.pressedBackground());
      JBColor BUTTON_SELECTED_HOVERED_BACKGROUND =
        JBColor.namedColor("SearchOption.selectedHoveredBackground", JBUI.CurrentTheme.ActionButton.pressedBackground());
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
        if (active) {
          return activeBG;
        }
        else {
          return JBColor.namedColor("MainToolbar.inactiveBackground", activeBG);
        }
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

      public static int menuPopupMinWidth() {
        return scale(180);
      }
    }

    public static final class DefaultTabs {
      public static final JBValue.UIInteger UNDERLINE_HEIGHT = new JBValue.UIInteger("DefaultTabs.underlineHeight", 3);

      public static @NotNull Color underlineColor() {
        return JBColor.namedColor("DefaultTabs.underlineColor", new JBColor(0x4083C9, 0x4A88C7));
      }

      /**
       * @deprecated use {@link DefaultTabs#UNDERLINE_HEIGHT}
       */
      @Deprecated(forRemoval = true)
      public static int underlineHeight() {
        return UNDERLINE_HEIGHT.get();
      }

      public static @NotNull Color inactiveUnderlineColor() {
        return JBColor.namedColor("DefaultTabs.inactiveUnderlineColor", new JBColor(0x9ca7b8, 0x747a80));
      }

      public static @NotNull Color borderColor() {
        return JBColor.namedColor("DefaultTabs.borderColor", JBColor.border());
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
        return uiIntValue("DebuggerTabs.underlineHeight", 2).get();
      }

      public static Color underlinedTabBackground() {
        return UIManager.getColor("DebuggerTabs.underlinedTabBackground");
      }

      public static String tabHeightKey() {
        return "DebuggerTabs.tabHeight";
      }

      public static int tabHeight() {
        return getInt(tabHeightKey(), 35);
      }

      public static @NotNull Font font() {
        return getFontWithSizeOffset(fontSizeOffsetKey(), JBFont.label());
      }

      public static String fontSizeOffsetKey() {
        return "DebuggerTabs.fontSizeOffset";
      }
    }

    public static final class EditorTabs {
      public static @NotNull Color underlineColor() {
        return JBColor.namedColor("EditorTabs.underlineColor", DefaultTabs.underlineColor());
      }

      public static int underlineHeight() {
        return uiIntValue(underlineHeightKey(), Math.round(DefaultTabs.UNDERLINE_HEIGHT.getUnscaled())).get();
      }

      public static String underlineHeightKey() {
        return "EditorTabs.underlineHeight";
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
        return insets(tabInsetsKey(), isNewUI() ? insets(-7, 8) : insets(0, 4));
      }

      public static String tabInsetsKey() {
        return "EditorTabs.tabInsets";
      }

      public static Insets verticalTabInsets() {
        return insets(verticalTabInsetsKey(), isNewUI() ? insets(-2, 8) : insets(0, 4));
      }

      public static String verticalTabInsetsKey() {
        return "EditorTabs.verticalTabInsets";
      }

      public static Insets tabContentInsets(@Nullable Boolean actionsOnTheRight) {
        if (actionsOnTheRight == null) {
          return insets(tabContentInsetsActionsNoneKey(), isNewUI() ? insetsLeft(4) : insets(0, 4));
        }
        else if (actionsOnTheRight) {
          return insets(tabContentInsetsActionsRightKey(), isNewUI() ? insets(0, 4, 0, 6) : insets(0, 4, 0, 2));
        }
        else {
          return insets(tabContentInsetsActionsLeftKey(), isNewUI() ? insetsLeft(6) : insets(0, 6, 0, 4));
        }
      }

      public static String tabContentInsetsActionsRightKey() {
        return "EditorTabs.tabContentActionsRightInsets";
      }

      public static String tabContentInsetsActionsLeftKey() {
        return "EditorTabs.tabContentActionsLeftInsets";
      }

      public static String tabContentInsetsActionsNoneKey() {
        return "EditorTabs.tabContentActionsNoneInsets";
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

      public static @NotNull Font font() {
        return getFontWithSizeOffset(fontSizeOffsetKey(), defaultFont());
      }

      public static String fontSizeOffsetKey() {
        return "EditorTabs.fontSizeOffset";
      }

      public static @NotNull JBFont defaultFont() {
        return JBFont.label();
      }

      public static float unselectedAlpha() {
        return getFloat("EditorTabs.unselectedAlpha", 1.0f);
      }

      public static float unselectedBlend() {
        return getFloat("EditorTabs.unselectedBlend", 1.0f);
      }
    }

    public interface Editor {
      Color BORDER_COLOR = JBColor.namedColor("Editor.Toolbar.borderColor", JBColor.border());

      interface SearchField {
        static @NotNull Insets borderInsets() {
          return insets(borderInsetsKey(),
                        isNewUI() ? insets(7, 10, 7, 8) :
                        insets(SystemInfo.isLinux ? 2 : 1));
        }

        static @NotNull String borderInsetsKey() {
          return "Editor.SearchField.borderInsets";
        }
      }

      interface SearchToolbar {
        static @NotNull Insets borderInsets() {
          return insets(borderInsetsKey(),
                        isNewUI() ? insetsTop(3) :
                        emptyInsets());
        }

        static @NotNull String borderInsetsKey() {
          return "Editor.SearchToolbar.borderInsets";
        }
      }

      interface ReplaceToolbar {
        static @NotNull Insets borderInsets() {
          return insets(borderInsetsKey(),
                        isNewUI() ? insetsTop(10) :
                        insetsTop(3));
        }

        static @NotNull String borderInsetsKey() {
          return "Editor.ReplaceToolbar.borderInsets";
        }
      }

      interface SearchReplaceModePanel {
        static @NotNull Insets borderInsets() {
          return insets(borderInsetsKey(), insets(7, 3));
        }

        static @NotNull String borderInsetsKey() {
          return "Editor.SearchReplaceModePanel.borderInsets";
        }
      }

      interface Tooltip {
        Color BACKGROUND = JBColor.namedColor("Editor.ToolTip.background", UIUtil.getToolTipBackground());
        Color FOREGROUND = JBColor.namedColor("Editor.ToolTip.foreground", UIUtil.getToolTipForeground());
        Color BORDER = JBColor.namedColor("Editor.ToolTip.border", 0xC9CCD6, 0x43454A);
        Color ERROR_BACKGROUND = JBColor.namedColor("Editor.ToolTip.errorBackground", 0x0, 0x0);
        Color ERROR_BORDER = JBColor.namedColor("Editor.ToolTip.errorBorder", 0x0, 0x0);
        Color SUCCESS_BACKGROUND = JBColor.namedColor("Editor.ToolTip.successBackground", 0xF2FCF3, 0x253627);
        Color SUCCESS_BORDER = JBColor.namedColor("Editor.ToolTip.successBorder", 0xAFDBB8, 0x375239);
        Color WARNING_BACKGROUND = JBColor.namedColor("Editor.ToolTip.warningBackground", 0xFFF6DE, 0x3D3223);
        Color WARNING_BORDER = JBColor.namedColor("Editor.ToolTip.warningBorder", 0xFED277, 0x5E4D33);
        Color ICON_HOVER_BACKGROUND = JBColor.namedColor("Editor.ToolTip.iconHoverBackground", ActionButton.hoverBorder());
        Color SELECTION_BACKGROUND = JBColor.namedColor("Editor.ToolTip.selectionBackground", 0xEBECF0, 0x2B2D30);
      }

      interface Notification {

        static @NotNull JBInsets borderInsets() {
          return insets(borderInsetsKey(), insets(10, 12));
        }

        static @NotNull String borderInsetsKey() {
          return "Editor.Notification.borderInsets";
        }

        static @NotNull JBInsets borderInsetsWithoutStatus() {
          return insets(
            borderInsetsKeyWithoutStatus(),
            insets(
              borderInsetsKey(), // fall back to the other key first to keep backwards compatibility
              isNewUI() ? JBInsets.create(9, 16) : JBInsets.create(5, 10)
            )
          );
        }

        static @NotNull String borderInsetsKeyWithoutStatus() {
          return "Editor.Notification.WithoutStatus.borderInsets";
        }
      }
      
      interface Gutter {

        static int emptyAnnotationAreaWidth() {
          return getInt(emptyAnnotationAreaWidthKey(), defaultEmptyAnnotationAreaWidth());
        }

        static @NotNull String emptyAnnotationAreaWidthKey() {
          return "Editor.Gutter.emptyAnnotationAreaWidth";
        }

        static int defaultEmptyAnnotationAreaWidth() {
          return 4;
        }

        static int gapAfterVcsMarkersWidth() {
          return getInt(gapAfterVcsMarkersWidthKey(), defaultGapAfterVcsMarkersWidth());
        }

        static @NotNull String gapAfterVcsMarkersWidthKey() {
          return "Editor.Gutter.gapAfterVcsMarkersWidth";
        }

        static int defaultGapAfterVcsMarkersWidth() {
          return 4;
        }

        static int gapAfterLineNumbersWidth() {
          return getInt(gapAfterLineNumbersWidthKey(), defaultGapAfterLineNumbersWidth());
        }

        static @NotNull String gapAfterLineNumbersWidthKey() {
          return "Editor.Gutter.gapAfterLineNumbersWidth";
        }

        static int defaultGapAfterLineNumbersWidth() {
          return 4;
        }

        static int gapAfterIconsWidth() {
          return getInt(gapAfterIconsWidthKey(), defaultGapAfterIconsWidth());
        }

        static @NotNull String gapAfterIconsWidthKey() {
          return "Editor.Gutter.gapAfterIconsWidth";
        }

        static int defaultGapAfterIconsWidth() {
          return 4;
        }
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
      Color NEW_UI = JBColor.namedColor("IconBadge.newUiBackground", 0x8F5AE5, 0x8F5AE5);
      Color ERROR = JBColor.namedColor("IconBadge.errorBackground", 0xE55765, 0xDB5C5C);
      Color WARNING = JBColor.namedColor("IconBadge.warningBackground", 0xFFAF0F, 0xF2C55C);
      Color INFORMATION = JBColor.namedColor("IconBadge.infoBackground", 0x588CF3, 0x548AF7);
      Color SUCCESS = JBColor.namedColor("IconBadge.successBackground", 0x55A76A, 0x5FAD65);
    }

    public interface InlineBanner {
      Color HOVER_BACKGROUND = JBColor.namedColor("InlineBanner.hoverBackground", ActionButton.hoverBorder());
      Color PRESSED_BACKGROUND = JBColor.namedColor("InlineBanner.pressedBackground", ActionButton.pressedBackground());
    }

    public interface Notification {
      Color FOREGROUND = JBColor.namedColor("Notification.foreground", Label.foreground());
      Color BACKGROUND = JBColor.namedColor("Notification.background", 0xFFF8D1, 0x1D3857);
      Color ICON_HOVER_BACKGROUND = JBColor.namedColor("Notification.iconHoverBackground", ActionButton.hoverBorder());

      interface Error {
        Color FOREGROUND = JBColor.namedColor("Notification.errorForeground", Notification.FOREGROUND);
        Color BACKGROUND = JBColor.namedColor("Notification.errorBackground", 0xF5E6E7, 0x593D41);
        Color BORDER_COLOR = JBColor.namedColor("Notification.errorBorderColor", 0xE0A8A9, 0x73454B);
      }
    }

    public static final class ProgressBar {
      public static final Color PROGRESS = JBColor.namedColor("ProgressBar.progressColor", new JBColor(0x808080, 0xA0A0A0));

      public static final Color INDETERMINATE_START = JBColor.namedColor("ProgressBar.indeterminateStartColor", new JBColor(0xC4C4C4, 0x696969));
      public static final Color INDETERMINATE_END = JBColor.namedColor("ProgressBar.indeterminateEndColor", new JBColor(0x808080, 0x838383));

      public static final Color PASSED = JBColor.namedColor("ProgressBar.passedColor", new JBColor(0x55A76A, 0x4E8052));
      public static final Color WARNING = JBColor.namedColor("ProgressBar.warningColor", new JBColor(0xF0A732, 0xD9A343));
      public static final Color FAILED = JBColor.namedColor("ProgressBar.failedColor", new JBColor(0xE55765, 0x9C4E4E));

      public static final Color PASSED_END = JBColor.namedColor("ProgressBar.passedEndColor", new JBColor(0x7ee8a5, 0x5dc48f));
      public static final Color WARNING_END = JBColor.namedColor("ProgressBar.warningEndColor", new JBColor(0xEAD2A1, 0xEAD2A1));
      public static final Color FAILED_END = JBColor.namedColor("ProgressBar.failedEndColor", new JBColor(0xfb8f89, 0xf4a2a0));

      public static final Color TRACK = JBColor.namedColor("ProgressBar.trackColor", new JBColor(0x55A76A, 0x4E8052));

      public static @NotNull String statusKey() { return "ProgressBar.status"; }
      public static @NotNull String passedStatusValue() { return "passed"; }
      public static @NotNull String warningStatusValue() { return "value"; }
      public static @NotNull String failedStatusValue() { return "failed"; }
    }

    public static final class Spinner {
      public static @NotNull Dimension minimumSize() {
        return size("Spinner.minimumSize", size(72, 0));
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

      public static @NotNull Font font() {
        return getFontWithSizeOffset(fontSizeOffsetKey(), defaultFont());
      }

      public static @NotNull String fontSizeOffsetKey() {
        return "StatusBar.fontSizeOffset";
      }

      public static @NotNull JBFont defaultFont() {
        return JBFont.label();
      }

      public interface Widget {
        Color FOREGROUND = JBColor.namedColor("StatusBar.Widget.foreground", UIUtil.getLabelForeground());
        Color HOVER_FOREGROUND = JBColor.namedColor("StatusBar.Widget.hoverForeground", UIUtil.getLabelForeground());
        Color HOVER_BACKGROUND = JBColor.namedColor("StatusBar.Widget.hoverBackground", ActionButton.hoverBackground());
        Color PRESSED_FOREGROUND = JBColor.namedColor("StatusBar.Widget.pressedForeground", UIUtil.getLabelForeground());
        Color PRESSED_BACKGROUND = JBColor.namedColor("StatusBar.Widget.pressedBackground", ActionButton.pressedBackground());

        static Border iconBorder() {
          return NamedBorderKt.withName(
            new JBEmptyBorder(insets(insetsKey(), isNewUI() ? insets(6, 8) : insets(0, 4))),
            iconBorderName()
          );
        }

        static @NotNull String iconBorderName() {
          return "StatusBar.Widget.iconBorder";
        }

        static Border border() {
          return NamedBorderKt.withName(
            new JBEmptyBorder(insets(insetsKey(), isNewUI() ? insets(6, 8) : insets(0, 6))),
            borderName()
          );
        }

        static @NotNull String borderName() {
          return "StatusBar.Widget.border";
        }

        static @NotNull String insetsKey() {
          return "StatusBar.Widget.widgetInsets";
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
          return insets("StatusBar.Breadcrumbs.floatingToolbarInsets", isNewUI() ? insets(8, 12) : emptyInsets());
        }

        static @NotNull Insets navBarInsets() {
          return insets(navBarInsetsKey(), defaultNavBarInsets());
        }

        static @NotNull JBInsets defaultNavBarInsets() {
          return insets(3, 0, 4, 4);
        }

        static @NotNull JBInsets itemInsets() {
          return insets("StatusBar.Breadcrumbs.itemInsets", isNewUI() ? emptyInsets() : insets(2, 0));
        }

        static @NotNull String navBarInsetsKey() {
          return "StatusBar.Breadcrumbs.navBarInsets";
        }

      }

    }

    public static final class TextField {
      public static @NotNull Dimension minimumSize() {
        return size("TextField.minimumSize", size(49, 24));
      }
    }

    public static final class ToolWindow {
      public static @NotNull Color background() {
        return JBColor.namedColor("ToolWindow.background");
      }

      public static @NotNull Color borderColor() {
        return JBColor.border();
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

      public static int headerTabUnderlineArc() {
        return getInt("ToolWindow.HeaderTab.underlineArc", 4);
      }

      public static JBInsets headerTabLeftRightInsets() {
        return insets("ToolWindow.HeaderTab.leftRightInsets", isNewUI() ? insets(0, 12) : insets(0, 8));
      }

      public interface DragAndDrop {
        Color STRIPE_BACKGROUND = JBColor.namedColor("ToolWindow.Button.DragAndDrop.stripeBackground", CurrentTheme.DragAndDrop.Area.BACKGROUND);
        Color BUTTON_DROP_BACKGROUND = JBColor.namedColor("ToolWindow.Button.DragAndDrop.buttonDropBackground", CurrentTheme.DragAndDrop.Area.BACKGROUND);
        Color BUTTON_FLOATING_BACKGROUND = JBColor.namedColor("ToolWindow.Button.DragAndDrop.buttonFloatingBackground", ActionButton.pressedBackground());
        Color BUTTON_DROP_BORDER = JBColor.namedColor("ToolWindow.Button.DragAndDrop.buttonDropBorderColor", new JBColor(0x407BF2, 0x548AF7));
        Color AREA_BACKGROUND = JBColor.namedColor("ToolWindow.DragAndDrop.areaBackground", new JBColor(0xA0BDF8, 0x366ACF));
      }

      public static @NotNull Color stripeSeparatorColor(boolean dnd) {
        return dnd
               ? JBColor.namedColor("ToolWindow.Stripe.DragAndDrop.separatorColor", 0x407BF2, 0x548AF7)
               : JBColor.namedColor("ToolWindow.Stripe.separatorColor", 0xC9CCD6, 0x43454A);
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

      public static int headerHeight() {
        return getInt(headerHeightKey(), defaultHeaderHeight());
      }

      public static @NotNull String headerHeightKey() {
        return "ToolWindow.Header.height";
      }

      public static int defaultHeaderHeight() {
        return 41;
      }

      public static JBInsets headerLabelLeftRightInsets() {
        return insets("ToolWindow.Header.labelLeftRightInsets", insets(0, 12, 0, 16));
      }

      public static JBInsets headerToolbarLeftRightInsets() {
        return insets("ToolWindow.Header.toolbarLeftRightInsets", insets(0, 12, 0, 8));
      }

      public static int underlineHeight() {
        return uiIntValue("ToolWindow.HeaderTab.underlineHeight", Math.round(DefaultTabs.UNDERLINE_HEIGHT.getUnscaled())).get();
      }


      public static @NotNull Font headerFont() {
        return getFontWithSizeOffset(headerFontSizeOffsetKey(), defaultHeaderFont());
      }

      public static @NotNull String headerFontSizeOffsetKey() {
        return "ToolWindow.Header.fontSizeOffset";
      }

      public static @NotNull JBFont defaultHeaderFont() {
        return JBFont.label();
      }

      public static float overrideHeaderFontSizeOffset() {
        Object offset = UIManager.get("ToolWindow.Header.font.size.offset");
        if (offset instanceof Integer) {
          return ((Integer)offset).floatValue();
        }

        return 0;
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

    public static final class Toolbar {

      public static final Color SEPARATOR_COLOR = JBColor.namedColor("ToolBar.separatorColor", CustomFrameDecorations.separatorForeground());

      public static Insets toolbarButtonInsets(boolean isMainToolbar) {
        return isMainToolbar ? mainToolbarButtonInsets() : toolbarButtonInsets();
      }

      public static Insets toolbarButtonInsets() {
        return insets("Toolbar.Button.buttonInsets", JBInsets.create(1, 2));
      }

      public static @Nullable Insets verticalToolbarInsets() {
        return isNewUI() ? insets(verticalInsetsKey(), insets(5, 7)) :
               UIManager.getInsets(verticalInsetsKey());
      }

      public static @NotNull String verticalInsetsKey() {
        return "ToolBar.verticalToolbarInsets";
      }

      public static @Nullable Insets horizontalToolbarInsets() {
        return isNewUI() ? insets(horizontalInsetsKey(), insets(5, 7)) :
               UIManager.getInsets(horizontalInsetsKey());
      }

      public static @NotNull String horizontalInsetsKey() {
        return "ToolBar.horizontalToolbarInsets";
      }

      public static Insets mainToolbarButtonInsets() {
        return insets(mainToolbarButtonInsetsKey(), isNewUI() ? emptyInsets() : insets(1, 2));
      }

      public static @NotNull String mainToolbarButtonInsetsKey() {
        return "MainToolbar.Icon.insets";
      }

      public static @NotNull Dimension experimentalToolbarButtonSize() {
        return size(experimentalToolbarButtonSizeKey(), defaultExperimentalToolbarButtonSize());
      }

      public static @NotNull String experimentalToolbarButtonSizeKey() {
        return "MainToolbar.Button.size";
      }

      public static @NotNull JBDimension defaultExperimentalToolbarButtonSize() {
        return size(30, 30);
      }

      public static int experimentalToolbarButtonIconSize() {
        return getInt(experimentalToolbarButtonIconSizeKey(), defaultExperimentalToolbarButtonIconSize());
      }

      public static @NotNull String experimentalToolbarButtonIconSizeKey() {
        return "MainToolbar.Button.iconSize";
      }

      public static int defaultExperimentalToolbarButtonIconSize() {
        return 20;
      }

      public static @NotNull Font experimentalToolbarFont() {
        return getFontWithSizeOffset(experimentalToolbarFontSizeOffsetKey(), defaultExperimentalToolbarFont());
      }

      public static @NotNull String experimentalToolbarFontSizeOffsetKey() {
        return "MainToolbar.fontSizeOffset";
      }

      public static @NotNull JBFont defaultExperimentalToolbarFont() {
        return JBFont.label();
      }

      public static int burgerMenuButtonIconSize() {
        return 20;
      }

      public static @NotNull Dimension stripeToolbarButtonSize() {
        return size(stripeToolbarButtonSizeKey(), defaultStripeToolbarButtonSize());
      }

      public static @NotNull String stripeToolbarButtonSizeKey() {
        return "StripeToolbar.Button.size";
      }

      public static @NotNull JBDimension defaultStripeToolbarButtonSize() {
        return size(40, 40);
      }

      public static int stripeToolbarButtonIconSize() {
        return getInt(stripeToolbarButtonIconSizeKey(), defaultStripeToolbarButtonIconSize());
      }

      public static @NotNull String stripeToolbarButtonIconSizeKey() {
        return "StripeToolbar.Button.iconSize";
      }

      public static int defaultStripeToolbarButtonIconSize() {
        return 20;
      }

      public static @NotNull Insets stripeToolbarButtonIconPadding(boolean compactMode, boolean showNames) {
        return insets(stripeToolbarButtonIconPaddingKey(), showNames
                                                           ? compactMode
                                                             ? defaultStripeToolbarButtonIconPaddingForCompactMode()
                                                             : defaultStripeToolbarButtonIconPaddingForNames()
                                                           : defaultStripeToolbarButtonIconPadding());
      }

      public static @NotNull String stripeToolbarButtonIconPaddingKey() {
        return "StripeToolbar.Button.iconPadding";
      }

      public static @NotNull JBInsets defaultStripeToolbarButtonIconPadding() {
        return insets(5);
      }

      public static @NotNull JBInsets defaultStripeToolbarButtonIconPaddingForNames() {
        return insets(4);
      }

      public static @NotNull JBInsets defaultStripeToolbarButtonIconPaddingForCompactMode() {
        return insets(3);
      }
    }

    public static final class MainToolbar {

      public static final class Dropdown {

        /**
         * @deprecated This method supposed to be used for deprecated ToolbarComboWidget only.
         */
        @Deprecated
        public static @NotNull Insets borderInsets() {
          return insets("MainToolbar.Dropdown.borderInsets", isNewUI() ? insets(5, 10, 5, 6) : insets(3, 5));
        }

        public static @NotNull Insets margin() {
          return borderInsets();
        }

        public static @NotNull JBValue hoverArc() {
          return new JBValue.UIInteger("MainToolbar.Dropdown.arc", 12);
        }
      }

      public static final class SplitDropdown {

        /**
         * @deprecated This method supposed to be used for deprecated ToolbarComboWidget only.
         */
        @Deprecated
        public static @NotNull Insets borderInsets() {
          return insets("MainToolbar.SplitDropdown.borderInsets", isNewUI() ? insets(5, 5, 5, 3) : insets(3, 5));
        }

        @NotNull public static Insets separatorMargin() {
          return insets("MainToolbar.SplitDropdown.separatorMargin", insets(5, 3));
        }

        @NotNull public static Insets leftPartMargin() {
          return insets("MainToolbar.SplitDropdown.leftPartMargin", insets(3, 7));
        }
        @NotNull public static Insets rightPartMargin() {
          return insets("MainToolbar.SplitDropdown.rightPartMargin", insets(3));
        }
      }

      public static final class Button {
        public static @NotNull JBValue hoverArc() {
          return new JBValue.UIInteger("MainToolbar.Button.arc", 12);
        }
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

      public static @NotNull Color errorForeground() {
        return NamedColorUtil.getErrorForeground();
      }

      public static @NotNull Color warningForeground() {
        return JBColor.namedColor("Label.warningForeground", 0xA46704, 0xBA9752);
      }
    }

    public static final class CompletionPopup {

      public static @NotNull Insets selectionInnerInsets() {
        return insets(selectionInnerInsetsKey(), insets(2));
      }

      public static @NotNull String selectionInnerInsetsKey() {
        return "CompletionPopup.selectionInnerInsets";
      }

      public static final class Advertiser {

        public static @NotNull Color background() {
          return JBColor.namedColor("CompletionPopup.Advertiser.background", CurrentTheme.Advertiser.background());
        }

        public static @NotNull Color foreground() {
          return JBColor.namedColor("CompletionPopup.Advertiser.foreground", CurrentTheme.Advertiser.foreground());
        }

        public static int fontSizeOffset() {
          return getInt("CompletionPopup.Advertiser.fontSizeOffset", CurrentTheme.Advertiser.FONT_SIZE_OFFSET.get());
        }

        public static @NotNull Border border() {
          return new EmptyBorder(insets(borderInsetsKey(),
                                        isNewUI() ? insets(4, 12, 3, 8) : CurrentTheme.Advertiser.borderInsets()));
        }

        public static @NotNull String borderInsetsKey() {
          return "CompletionPopup.Advertiser.borderInsets";
        }
      }
    }

    public static final class FindPopup {
      public static @NotNull Insets scopesPanelInsets() {
        return insets(scopesPanelInsetsKey(), isNewUI() ? insets(4, 20) : insets(3, 5));
      }

      public static @NotNull String scopesPanelInsetsKey() {
        return "FindPopup.scopesPanelInsets";
      }

      public static @NotNull Insets bottomPanelInsets() {
        return insets(bottomPanelInsetsKey(), isNewUI() ? insets(5, 18) : insets(5));
      }

      public static @NotNull String bottomPanelInsetsKey() {
        return "FindPopup.bottomPanelInsets";
      }
    }

    public static final class ComplexPopup {

      public static final Color HEADER_BACKGROUND = JBColor.namedColor("ComplexPopup.Header.background", Popup.BACKGROUND);

      public static JBInsets headerInsets() {
        return insets(headerInsetsKey(), insets(13, 20, 11, 15));
      }

      public static @NotNull String headerInsetsKey() {
        return "ComplexPopup.Header.insets";
      }

      public static JBInsets textFieldBorderInsets() {
        return insets("ComplexPopup.TextField.borderInsets", insets(0, 12));
      }

      public static JBInsets textFieldInputInsets() {
        return insets(textFieldInputInsetsKey(), insets(6, 2));
      }

      public static @NotNull String textFieldInputInsetsKey() {
        return "ComplexPopup.TextField.inputInsets";
      }

      public static final int TEXT_FIELD_SEPARATOR_HEIGHT = 1;

      public static JBInsets innerBorderInsets() {
        return insets(
          innerBorderInsetsKey(),
          insets(6, 3, 6, headerInsets().getUnscaled().right - textFieldBorderInsets().getUnscaled().right)
        );
      }

      public static @NotNull String innerBorderInsetsKey() {
        return "ComplexPopup.innerBorderInsets";
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

      public static @NotNull Insets headerInsets() {
        return insets(headerInsetsKey(), insets(12, 10, 10, 10));
      }

      public static @NotNull String headerInsetsKey() {
        return "Popup.Header.insets";
      }

      public static int headerHeight(boolean hasControls) {
        return hasControls ? JBUIScale.scale(28) : JBUIScale.scale(24);
      }

      public static final Color BACKGROUND = JBColor.namedColor("Popup.background", List.BACKGROUND);

      public static Color borderColor(boolean active) {
        return active
               ? JBColor.namedColor("Popup.borderColor", JBColor.namedColor("Popup.Border.color", 0x808080))
               : JBColor.namedColor("Popup.inactiveBorderColor", JBColor.namedColor("Popup.inactiveBorderColor", 0xaaaaaa));
      }

      public static float borderWidth() {
        return getFloat("Popup.borderWidth", 1);
      }

      public static Insets searchFieldBorderInsets() {
        return insets("Popup.SearchField.borderInsets", insets(4, 12));
      }

      public static Insets searchFieldInputInsets() {
        return insets("Popup.SearchField.inputInsets", insets(4, 8, 8, 2));
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
        return insets("Popup.separatorInsets", insets(4, 8));
      }

      public static Insets separatorLabelInsets() {
        return insets("Popup.separatorLabelInsets", insets(3, 16));
      }

      public static Color separatorTextColor() {
        return JBColor.namedColor("Popup.separatorForeground", Color.gray);
      }

      public static int minimumHintWidth() {
        return JBUIScale.scale(170);
      }

      public static Color mnemonicForeground() {
        return JBColor.namedColor("Popup.mnemonicForeground", ActionsList.MNEMONIC_FOREGROUND);
      }

      public static final class Selection {
        public static final JBValue ARC = new JBValue.UIInteger("Popup.Selection.arc", 8);
        public static final JBValue LEFT_RIGHT_INSET = new JBValue.UIInteger("Popup.Selection.leftRightInset", 8);

        public static @NotNull Insets innerInsets() {
          JBInsets result = insets("Popup.Selection.innerInsets", insets(0, 8));
          // Top and bottom values are ignored now
          result.top = 0;
          result.bottom = 0;
          return result;
        }
      }
    }

    public static final class Menu {

      public static final class Selection {
        public static @NotNull JBInsets innerInsets() {
          return insets("Menu.Selection.innerInsets", isNewUI() ? insets(0, 6) : insets(2));
        }

        public static @NotNull JBInsets outerInsets() {
          return insets("Menu.Selection.outerInsets", insets(1, 4));
        }

        public static final JBValue ARC = new JBValue.UIInteger("Menu.Selection.arc", 8);
      }
    }

    public static final class PopupMenu {

      public static final class Selection {
        public static @NotNull JBInsets innerInsets() {
          return insets("PopupMenu.Selection.innerInsets", isNewUI() ? insets(0, 6) : insets(2, 10));
        }

        public static @NotNull JBInsets outerInsets() {
          return insets("PopupMenu.Selection.outerInsets", insets(1, 7));
        }

        public static final JBValue ARC = new JBValue.UIInteger("PopupMenu.Selection.arc", 8);
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
      public static final JBValue TAB_HEIGHT = new JBValue.UIInteger(tabHeightKey(), 32);
      public static @NotNull String tabHeightKey() { return "TabbedPane.tabHeight"; }
      public static final JBValue SELECTION_HEIGHT = new JBValue.UIInteger("TabbedPane.tabSelectionHeight", 3);
      public static final JBValue SELECTION_ARC = new JBValue.UIInteger("TabbedPane.tabSelectionArc", 0);
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

      public static final @NotNull Color LIST_SETTINGS_BACKGROUND =
        JBColor.namedColor("SearchEverywhere.List.settingsBackground", LightColors.SLIGHTLY_GRAY);

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
        return new JBEmptyBorder(insets(advertiserBorderInsetsKey(),
                                        isNewUI() ? insets(6, 20) : insets(5, 10, 5, 15)));
      }

      public static @NotNull String advertiserBorderInsetsKey() {
        return "SearchEverywhere.Advertiser.borderInsets";
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
        return new EmptyBorder(borderInsets());
      }

      private static @NotNull JBInsets borderInsets() {
        return insets(borderInsetsKey(), isNewUI() ? insets(6, 20) : insets(5, 10, 5, 15));
      }

      public static @NotNull String borderInsetsKey() {
        return "Popup.Advertiser.borderInsets";
      }

      public static @NotNull Color borderColor() {
        return JBColor.namedColor("Popup.Advertiser.borderColor", Gray._135);
      }

      public static final JBValue FONT_SIZE_OFFSET = new JBValue.UIInteger("Popup.Advertiser.fontSizeOffset", -2);
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

    public static final class VersionControl {

      public static final class Log {
        public static int rowHeight() {
          return getInt(rowHeightKey(), defaultRowHeight());
        }

        public static @NotNull String rowHeightKey() {
          return "VersionControl.Log.Commit.rowHeight";
        }

        public static int defaultRowHeight() {
          return 26;
        }

        public static int verticalPadding() {
          return getInt(verticalPaddingKey(), defaultVerticalPadding());
        }

        public static @NotNull String verticalPaddingKey() {
          return "VersionControl.Log.Commit.verticalPadding";
        }

        public static int defaultVerticalPadding() {
          return 7;
        }
      }

      public static final class CombinedDiff {
        public static @NotNull Insets mainToolbarInsets() {
          return insets(mainToolbarInsetsKey(), isNewUI() ? insets(4, 10) : insets(1, 10));
        }

        public static @NotNull String mainToolbarInsetsKey() {
          return "CombinedDiff.mainToolbarInsets";
        }

        public static @NotNull Insets fileToolbarInsets() {
          return insets(fileToolbarInsetsKey(), isNewUI() ? insets(12, 10) : insets(7, 10));
        }

        public static @NotNull String fileToolbarInsetsKey() {
          return "CombinedDiff.fileToolbarInsets";
        }

        public static int leftRightBlockInset() {
          return getInt(leftRightBlockInsetKey(), 12);
        }

        public static @NotNull String leftRightBlockInsetKey() {
          return "CombinedDiff.leftRightBlockInset";
        }

        public static int gapBetweenBlocks() {
          return getInt(gapBetweenBlocksKey(), 10);
        }

        public static @NotNull String gapBetweenBlocksKey() {
          return "CombinedDiff.gapBetweenBlocks";
        }
      }
    }

    public interface Window {
      static Border getBorder(boolean undecoratedWindow) {
        Border result = UIManager.getBorder("Window.border");
        if (result == null && undecoratedWindow &&
            SystemInfo.isUnix && !SystemInfo.isMac && Registry.is("ide.linux.use.undecorated.border")) {
          result = UIManager.getBorder("Window.undecorated.border");
        }
        return result;
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
      @Deprecated(forRemoval = true)
      public static @NotNull Color linkColor() {
        return Foreground.ENABLED;
      }
    }

    public static final class Tooltip {
      public static final JBValue CORNER_RADIUS = new JBValue.UIInteger("ToolTip.borderCornerRadius", 4);

      public static @NotNull Color shortcutForeground () {
        return JBColor.namedColor("ToolTip.shortcutForeground", new JBColor(0x787878, 0x999999));
      }

      /**
       * Border color for tooltips except information/question/error tooltips (see {@link com.intellij.codeInsight.hint.HintUtil#HINT_BORDER_COLOR_KEY})
       */
      public static @NotNull Color borderColor() {
        return JBColor.namedColor("ToolTip.borderColor", new JBColor(0xadadad, 0x636569));
      }
    }

    public static final class GotItTooltip {
      public static final JBValue TEXT_INSET = new JBValue.UIInteger("GotItTooltip.textInset", 4);
      public static final JBValue BUTTON_TOP_INSET = new JBValue.UIInteger("GotItTooltip.buttonTopInset", 12);
      public static final JBValue BUTTON_BOTTOM_INSET = new JBValue.UIInteger("GotItTooltip.buttonBottomInset", 6);
      public static final JBValue ICON_INSET = new JBValue.UIInteger("GotItTooltip.iconInset", 6);
      public static final JBValue IMAGE_TOP_INSET = new JBValue.UIInteger("GotItTooltip.imageTopInset", 4);
      public static final JBValue IMAGE_BOTTOM_INSET = new JBValue.UIInteger("GotItTooltip.imageBottomInset", 12);
      public static final JBValue CORNER_RADIUS = new JBValue.UIInteger("GotItTooltip.arc", 8);

      // Balloon itself has insets of 5 for top and bottom and 8 for left and right.
      // So totally there are 12 for top and bottom and 16 for left and right
      public static @NotNull Insets insets() {
        return JBUI.insets("GotItTooltip.insets", JBUI.insets(7, 8));
      }

      public static @NotNull Color foreground(boolean useContrastColors) {
        Color base = JBColor.namedColor("GotItTooltip.foreground", UIUtil.getToolTipForeground());
        if (useContrastColors) {
          return JBColor.namedColor("Tooltip.Learning.foreground", base);
        }
        return base;
      }

      public static @NotNull Color background(boolean useContrastColors) {
        Color base = JBColor.namedColor("GotItTooltip.background", UIUtil.getToolTipBackground());
        if (useContrastColors) {
          return JBColor.namedColor("Tooltip.Learning.background", base);
        }
        return base;
      }

      public static @NotNull Color stepForeground(boolean useContrastColors) {
        Color base = JBColor.namedColor("GotItTooltip.stepForeground", foreground(false));
        if (useContrastColors) {
          return JBColor.namedColor("Tooltip.Learning.stepNumberForeground", base);
        }
        return base;
      }

      public static @NotNull Color secondaryActionForeground(boolean useContrastColors) {
        Color base = JBColor.namedColor("GotItTooltip.secondaryActionForeground", foreground(false));
        if (useContrastColors) {
          return JBColor.namedColor("Tooltip.Learning.secondaryActionForeground", base);
        }
        return base;
      }

      public static @NotNull Color linkUnderline(boolean useContrastColors, boolean isActive, Color defaultActiveColor) {
        if (useContrastColors) {
          if (isActive) return JBColor.namedColor("Tooltip.Learning.linkUnderlineHoveredColor", defaultActiveColor);
          else return JBColor.namedColor("Tooltip.Learning.linkUnderlineDefaultColor", CLEAR);
        }
        else {
          if (isActive) return JBColor.namedColor("GotItTooltip.linkUnderlineHoveredColor", defaultActiveColor);
          else return JBColor.namedColor("GotItTooltip.linkUnderlineDefaultColor", CLEAR);
        }
      }

      public static @NotNull Color headerForeground(boolean useContrastColors) {
        Color base = JBColor.namedColor("GotItTooltip.Header.foreground", foreground(false));
        if (useContrastColors) {
          return JBColor.namedColor("Tooltip.Learning.Header.foreground", base);
        }
        return base;
      }

      public static @NotNull Color shortcutForeground(boolean useContrastColors) {
        Color base = JBColor.namedColor("GotItTooltip.shortcutForeground", foreground(false));
        if (useContrastColors) {
          return JBColor.namedColor("Tooltip.Learning.spanForeground", base);
        }
        return base;
      }

      public static @NotNull Color shortcutBackground(boolean useContrastColors) {
        Color base = JBColor.namedColor("GotItTooltip.shortcutBackground",
                                        JBColor.namedColor("Lesson.shortcutBackground", 0xE6EEF7, 0x333638));
        if (useContrastColors) {
          return JBColor.namedColor("Tooltip.Learning.spanBackground", base);
        }
        return base;
      }

      public static @NotNull Color shortcutBorder(boolean useContrastColors) {
        if (useContrastColors) return shortcutBackground(useContrastColors);
        else return JBColor.namedColor("GotItTooltip.shortcutBorderColor", shortcutBackground(useContrastColors));
      }

      public static @NotNull Color codeForeground(boolean useContrastColors) {
        Color base = JBColor.namedColor("GotItTooltip.codeForeground", foreground(useContrastColors));
        if (useContrastColors) {
          return JBColor.namedColor("Tooltip.Learning.codeForeground", base);
        }
        return base;
      }

      public static @NotNull Color codeBackground(boolean useContrastColors) {
        Color base = background(useContrastColors);
        if (useContrastColors) {
          return base;
        }
        return JBColor.namedColor("GotItTooltip.codeBackground", base);
      }

      public static @NotNull Color codeBorderColor(boolean useContrastColors) {
        Color base = JBColor.namedColor("GotItTooltip.codeBorderColor", shortcutBackground(useContrastColors));
        if (useContrastColors) {
          return JBColor.namedColor("Tooltip.Learning.codeBorderColor", base);
        }
        return base;
      }

      public static @Nullable Color iconBorderColor(boolean useContrastColors) {
        Color base;
        if (useContrastColors) {
          base = JBColor.namedColor("Tooltip.Learning.iconBorderColor", CLEAR);
        }
        else {
          base = JBColor.namedColor("GotItTooltip.iconBorderColor", CLEAR);
        }

        if (base.getAlpha() == 0) return null;
        else return base;
      }

      public static @Nullable Color iconFillColor(boolean useContrastColors) {
        Color base;
        if (useContrastColors) {
          base = JBColor.namedColor("Tooltip.Learning.iconFillColor", CLEAR);
        }
        else {
          base = JBColor.namedColor("GotItTooltip.iconFillColor", CLEAR);
        }

        if (base.getAlpha() == 0) return null;
        else return base;
      }

      public static @NotNull Color linkForeground(boolean useContrastColors) {
        if (useContrastColors) return JBColor.namedColor("Tooltip.Learning.linkForeground", JBUI.CurrentTheme.Link.Foreground.ENABLED);
        else return JBColor.namedColor("GotItTooltip.linkForeground", JBUI.CurrentTheme.Link.Foreground.ENABLED);
      }

      public static @NotNull Color borderColor(boolean useContrastColors) {
        Color base = JBColor.namedColor("GotItTooltip.borderColor", JBUI.CurrentTheme.Tooltip.borderColor());
        if (useContrastColors) {
          return JBColor.namedColor("Tooltip.Learning.borderColor", base);
        }
        return base;
      }

      public static @NotNull Color imageBorderColor(boolean useContrastColors) {
        return JBColor.namedColor("GotItTooltip.imageBorderColor", background(useContrastColors));
      }

      public static @NotNull Color animationBackground(boolean useContrastColors) {
        return JBColor.namedColor("GotItTooltip.animationBackground", background(useContrastColors));
      }

      public static @NotNull Color buttonBackgroundContrast() {
        return JBColor.namedColor("Tooltip.Learning.spanBackground", 0x0D5CBD, 0x0250B0);
      }

      public static @NotNull Color buttonBackgroundContrastOnlyButton() {
        return JBColor.namedColor("GotItTooltip.Button.contrastBackground", buttonBackgroundContrast());
      }

      public static @NotNull Color buttonForeground() {
        return JBColor.namedColor("GotItTooltip.Button.foreground", JBColor.namedColor("Button.foreground"));
      }

      public static @NotNull Color buttonForegroundContrast() {
        return JBColor.namedColor("Tooltip.Learning.spanForeground", 0xF5F5F5);
      }
    }

    public static final class HelpTooltip {
      public static @NotNull Insets defaultTextBorderInsets() {
        return insets("HelpTooltip.defaultTextBorderInsets", isNewUI() ? insets(12, 16, 16, 16) : insets(8, 10, 10, 13));
      }

      public static @NotNull Insets smallTextBorderInsets() {
        return insets("HelpTooltip.smallTextBorderInsets", isNewUI() ? insets(8, 12, 9, 12) : insets(6, 10, 7, 12));
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
    
    public static final class NavBar {

      public static @NotNull Insets itemInsets() {
        return insets(itemInsetsKey(), defaultItemInsets());
      }

      public static @NotNull JBInsets defaultItemInsets() {
        return insets(4, 2);
      }

      public static @NotNull String itemInsetsKey() {
        return "NavBar.Breadcrumbs.itemInsets";
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
        return getInt("NewClass.separatorWidth", 10);
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
    private static final Color CLEAR = new Color(0, true);

    public interface List {
      Color BACKGROUND = JBColor.namedColor("List.background", DEFAULT_RENDERER_BACKGROUND);
      Color FOREGROUND = JBColor.namedColor("List.foreground", Label.foreground(false));

      static @NotNull Color background(boolean selected, boolean focused) {
        return selected ? Selection.background(focused) : BACKGROUND;
      }

      static @NotNull Color foreground(boolean selected, boolean focused) {
        return selected ? Selection.foreground(focused) : FOREGROUND;
      }

      static int rowHeight() {
        int defaultHeight = JBUIScale.scale(24);
        int result = getInt("List.rowHeight", defaultHeight);
        // Linux doesn't support rowHeight now, use default value. See IDEA-234112
        return result <= 0 ? defaultHeight : result;
      }

      static int buttonLeftRightInsets() {
        return getInt("List.Button.leftRightInset", 8);
      }

      static Color buttonHoverBackground() {
        return JBColor.namedColor("List.Button.hoverBackground");
      }

      static Color buttonSeparatorColor() {
        return JBColor.namedColor("List.Button.separatorColor", Popup.BACKGROUND);
      }

      static int buttonSeparatorInset() {
        return getInt("List.Button.separatorInset", 4);
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

      interface Tag {
        Color BACKGROUND = JBColor.namedColor("List.Tag.background", new JBColor(0xEBECF0, 0x393B40));
        Color FOREGROUND = JBColor.namedColor("List.Tag.foreground", new JBColor(0x494B57, 0xA8ADBD));
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
      JBValue ARC = new JBValue.UIInteger("Tree.Selection.arc", 8);

      static @NotNull Color background(boolean selected, boolean focused) {
        return selected ? Selection.background(focused) : BACKGROUND;
      }

      static @NotNull Color foreground(boolean selected, boolean focused) {
        return selected ? Selection.foreground(focused) : FOREGROUND;
      }

      static int rowHeight() {
        int defaultHeight = defaultRowHeight();
        int result = getInt(rowHeightKey(), defaultHeight);
        // Linux doesn't support rowHeight now, use default value. See IDEA-234112
        return result <= 0 ? defaultHeight : result;
      }

      static @NotNull String rowHeightKey() {
        return "Tree.rowHeight";
      }

      static int defaultRowHeight() {
        return JBUIScale.scale(24);
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

        public static boolean forceFocusedSelectionForeground() {
          return UIManager.getBoolean("Tree.forceFocusedSelectionForeground");
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

    public static final class RunWidget {
      public static final Color FOREGROUND = JBColor.namedColor("RunWidget.foreground", Color.WHITE);
      public static final Color ICON_COLOR = JBColor.namedColor("RunWidget.iconColor", Color.WHITE);
      public static final Color RUN_ICON_COLOR = JBColor.namedColor("RunWidget.runIconColor", new Color(0x5FAD65));
      public static final Color RUNNING_BACKGROUND = JBColor.namedColor("RunWidget.runningBackground", 0x599E5E);
      public static final Color RUNNING_ICON_COLOR = JBColor.namedColor("RunWidget.runningIconColor", Color.WHITE);
      public static final Color STOP_BACKGROUND = JBColor.namedColor("RunWidget.stopBackground", new Color(0xC94F4F));

      // these colors will be applied over background color
      public static final Color HOVER_BACKGROUND = JBColor.namedColor("RunWidget.hoverBackground", new Color(0, 0, 0, 25));
      public static final Color PRESSED_BACKGROUND = JBColor.namedColor("RunWidget.pressedBackground", new Color(0, 0, 0, 40));

      public static int toolbarHeight() {
        return getInt(toolbarHeightKey(), defaultToolbarHeight());
      }

      public static @NotNull String toolbarHeightKey() {
        return "RunWidget.toolbarHeight";
      }

      public static int defaultToolbarHeight() {
        return 30;
      }

      public static int toolbarBorderHeight() {
        return getInt(toolbarBorderHeightKey(), defaultToolbarBorderHeight());
      }

      public static @NotNull String toolbarBorderHeightKey() {
        return "RunWidget.toolbarBorderHeight";
      }

      public static int defaultToolbarBorderHeight() {
        return 5;
      }

      public static int toolbarBorderDirectionalGap() {
        return getInt(toolbarBorderDirectionalGapKey(), defaultToolbarBorderDirectionalGap());
      }

      public static @NotNull String toolbarBorderDirectionalGapKey() {
        return "RunWidget.toolbarBorderDirectionalGap";
      }

      public static int defaultToolbarBorderDirectionalGap() {
        return 2;
      }

      public static int actionButtonWidth() {
        return getInt(actionButtonWidthKey(), 30);
      }

      public static @NotNull String actionButtonWidthKey() {
        return "RunWidget.actionButtonWidth";
      }

      public static Font configurationSelectorFont() {
        return getFontWithSizeOffset(configurationSelectorFontSizeOffsetKey(), defaultConfigurationSelectorFont());
      }

      public static @NotNull String configurationSelectorFontSizeOffsetKey() {
        return "RunWidget.configurationSelectorFontSizeOffset";
      }

      public static JBFont defaultConfigurationSelectorFont() {
        return JBFont.label();
      }
    }

    public static final class TitlePane {

      public static @NotNull Dimension buttonPreferredSize(float scaleDefaultValues) {
        return size(buttonPreferredSizeKey(), size((int)(47 * scaleDefaultValues), (int)(28 * scaleDefaultValues)));
      }

      public static @NotNull String buttonPreferredSizeKey() {
        return "TitlePane.Button.preferredSize";
      }
    }

    public static final class MainWindow {
      public static final class Tab {
        private static final Color SELECTED_FOREGROUND = JBColor.namedColor("MainWindow.Tab.selectedForeground", 0xC9CCD6, 0xCED0D6);
        private static final Color SELECTED_BACKGROUND = JBColor.namedColor("MainWindow.Tab.selectedBackground", 0x27282E, 0x2B2D30);
        private static final Color SELECTED_INACTIVE_BACKGROUND =
          JBColor.namedColor("MainWindow.Tab.selectedInactiveBackground", 0x383A42, 0x393B40);
        private static final Color FOREGROUND = JBColor.namedColor("MainWindow.Tab.foreground", 0xA8ADBD, 0xB4B8BF);
        private static final Color BACKGROUND = JBColor.namedColor("MainWindow.Tab.background", 0x000000, 0x131314);
        private static final Color HOVER_FOREGROUND = JBColor.namedColor("MainWindow.Tab.hoverForeground", 0xC9CCD6, 0xCED0D6);
        private static final Color HOVER_BACKGROUND = JBColor.namedColor("MainWindow.Tab.hoverBackground", 0x171717, 0x1A1A1B);

        public static final Color SEPARATOR = JBColor.namedColor("MainWindow.Tab.separatorColor", 0x383A42, 0x2B2D30);
        public static final Color BORDER = JBColor.namedColor("MainWindow.Tab.borderColor", 0x383A42, 0x1E1F22);

        public static @NotNull Color foreground(boolean selection, boolean hovered) {
          if (selection) {
            return SELECTED_FOREGROUND;
          }
          return hovered ? HOVER_FOREGROUND : FOREGROUND;
        }

        public static @NotNull Color background(boolean selection, boolean inactive, boolean hovered) {
          if (selection) {
            return inactive ? SELECTED_INACTIVE_BACKGROUND : SELECTED_BACKGROUND;
          }
          return hovered ? HOVER_BACKGROUND : BACKGROUND;
        }
      }
    }

    public static final class ManagedIde {
      public static final Color BADGE_BORDER = JBColor.namedColor("ManagedIdeBadgeBorder", CustomFrameDecorations.separatorForeground());
      private static final Color BADGE_BACKGROUND = JBColor.namedColor("ManagedIdeBadgeBackground", CustomFrameDecorations.paneBackground());
      private static final Color BADGE_BACKGROUND_HOVER = JBColor.namedColor("ManagedIdeBadgeBackgroundHover", JBColor.namedColor(
        "MainToolbar.Dropdown.transparentHoverBackground", UIManager.getColor("MainToolbar.Icon.hoverBackground")));
      public static final Color MENU_ITEM_HOVER = JBColor.namedColor("ManagedIdeMenuItemHover", 0xEBECF0, 0x43454A);

      public static @NotNull Color getBadgeBackground(boolean hover) {
        return hover ? BADGE_BACKGROUND_HOVER : BADGE_BACKGROUND;
      }
    }

    @ApiStatus.Internal
    public static final class LicenseDialog {
      private static final @NotNull Color TERMS_AND_CONDITIONS_COLOR =
        JBColor.namedColor("LicenseDialog.termsAndConditionsForeground", 0x818594, 0x6F737A);

      private static final @NotNull Color WAITING_LABEL_COLOR =
        JBColor.namedColor("LicenseDialog.waitingLabelForeground", 0x818594, 0x6F737A);

      private static final @NotNull Color SEPARATOR_COLOR =
        JBColor.namedColor("LicenseDialog.separatorColor", 0xEBECF0, 0x393B40);

      public static @NotNull Color getTermsAndConditionsForeground() {
        return TERMS_AND_CONDITIONS_COLOR;
      }

      public static @NotNull Color getWaitingLabelColor() {
        return WAITING_LABEL_COLOR;
      }

      public static @NotNull Color getSeparatorColor() {
        return SEPARATOR_COLOR;
      }

      public static final class LicenseList {
        private static final @NotNull Color LICENSE_DETAILS_COLOR =
          JBColor.namedColor("LicenseDialog.LicenseList.licenseDetailsForeground", 0xC9CCD6, 0x9DA0A8);
        private static final @NotNull Color LICENSE_ID_COLOR =
          JBColor.namedColor("LicenseDialog.LicenseList.licenseIdForeground", 0xD3D5DB, 0x43454A);
        private static final @NotNull Color SEPARATOR_COLOR =
          JBColor.namedColor("LicenseDialog.LicenseList.separatorColor", 0xD3D5DB, 0x43454A);

        public static @NotNull Color getLicenseDetailsColor() {
          return LICENSE_DETAILS_COLOR;
        }

        public static @NotNull Color getLicenseIdColor() {
          return LICENSE_ID_COLOR;
        }

        public static @NotNull Color getSeparatorColor() {
          return SEPARATOR_COLOR;
        }
      }

      public static final class LicenseServer {
        private static final @NotNull Color SEPARATOR_COLOR =
          JBColor.namedColor("LicenseDialog.LicenseServer.separatorColor", 0xD3D5DB, 0x43454A);

        public static @NotNull Color getSeparatorColor() {
          return SEPARATOR_COLOR;
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

  private static @Nullable Font getFont(@NonNls @NotNull String propertyName) {
    return maybeConvertToFont(UIManager.get(propertyName));
  }

  private static @NotNull Font getFontWithSizeOffset(@NonNls @NotNull String propertyName, @NotNull JBFont baseFont) {
    return baseFont.biggerOn((float)getInt(propertyName, 0));
  }

  private static @Nullable Font maybeConvertToFont(Object maybeFont) {
    if (maybeFont instanceof Font font) {
      return font;
    }
    if (maybeFont instanceof Supplier<?> supplier) {
      return maybeConvertToFont(supplier.get());
    }
    return null;
  }

  private static boolean isNewUI() {
    return NewUiValue.isEnabled();
  }
}
