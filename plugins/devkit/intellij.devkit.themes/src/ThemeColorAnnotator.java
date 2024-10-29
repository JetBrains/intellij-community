// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes;

import com.intellij.codeInsight.daemon.LineMarkerSettings;
import com.intellij.json.psi.*;
import com.intellij.json.psi.impl.JsonPsiImplUtils;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ColorChooserService;
import com.intellij.ui.ColorLineMarkerProvider;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

final class ThemeColorAnnotator implements Annotator, DumbAware {

  private static final Pattern COLOR_HEX_PATTERN_RGB = Pattern.compile("^#([A-Fa-f0-9]{6})$");
  private static final Pattern COLOR_HEX_PATTERN_RGBA = Pattern.compile("^#([A-Fa-f0-9]{8})$");
  private static final int HEX_COLOR_LENGTH_RGB = 7;
  private static final int HEX_COLOR_LENGTH_RGBA = 9;

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (!isColorLineMarkerProviderEnabled() || !isTargetElement(element, holder.getCurrentAnnotationSession().getFile())) return;

    JsonStringLiteral literal = (JsonStringLiteral)element;
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
    .gutterIconRenderer(new MyRenderer(literal.getValue(), literal)).create();
  }

  private static boolean isColorLineMarkerProviderEnabled() {
    return LineMarkerSettings.getSettings().isEnabled(ColorLineMarkerProvider.INSTANCE);
  }

  static boolean isTargetElement(@NotNull PsiElement element) {
    return isTargetElement(element, element.getContainingFile());
  }

  private static boolean isTargetElement(@NotNull PsiElement element, @NotNull PsiFile containingFile) {
    if (!(element instanceof JsonStringLiteral)) return false;
    if (!ThemeJsonUtil.isThemeFilename(containingFile.getName())) return false;

    if (JsonPsiImplUtils.isPropertyName((JsonStringLiteral)element)) return false;
    String text = ((JsonStringLiteral)element).getValue();
    return isColorCode(text) || isNamedColor(text);
  }

  private static boolean isNamedColor(String text) {
    return StringUtil.isLatinAlphanumeric(text);
  }

  private static boolean isColorCode(@Nullable String text) {
    if (!StringUtil.startsWithChar(text, '#')) return false;
    //noinspection ConstantConditions - StringUtil#startsWithChar checks for null
    if (text.length() != HEX_COLOR_LENGTH_RGB && text.length() != HEX_COLOR_LENGTH_RGBA) return false;
    return COLOR_HEX_PATTERN_RGB.matcher(text).matches() || COLOR_HEX_PATTERN_RGBA.matcher(text).matches();
  }


  private static final class MyRenderer extends GutterIconRenderer implements DumbAware {
    private static final int ICON_SIZE = 12;

    private final String myColorText;
    private JsonStringLiteral myLiteral;


    private MyRenderer(@NotNull String colorText, @NotNull JsonStringLiteral literal) {
      myColorText = colorText;
      myLiteral = literal;
    }

    @NotNull
    @Override
    public Icon getIcon() {
      Color color = getColor(myColorText);
      if (color != null) {
        return JBUIScale.scaleIcon(new ColorIcon(ICON_SIZE, color));
      }
      return JBUIScale.scaleIcon(EmptyIcon.create(ICON_SIZE));
    }

    @Override
    public boolean isNavigateAction() {
      return canChooseColor();
    }

    @Nullable
    @Override
    public String getTooltipText() {
      return canChooseColor() ? DevKitThemesBundle.message("theme.choose.color.tooltip") : null;
    }

    @Nullable
    @Override
    public AnAction getClickAction() {
      if (!canChooseColor()) return null;

      return new AnAction(DevKitThemesBundle.messagePointer("action.Anonymous.text.choose.color")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          Editor editor = e.getData(CommonDataKeys.EDITOR);
          if (editor == null) return;

          Color currentColor = getColor(myColorText);
          if (currentColor == null) return;

          boolean withAlpha = isRgbaColorHex(myColorText);

          if (Registry.is("ide.new.color.picker")) {
            ColorChooserService.getInstance().showPopup(e.getProject(), currentColor, (c, l) -> applyColor(currentColor, withAlpha, c));
          } else {
            Color newColor = ColorChooserService.getInstance().showDialog(editor.getProject(),
                                                      editor.getComponent(),
                                                      DevKitThemesBundle.message("theme.choose.color.dialog.title"),
                                                      currentColor,
                                                      withAlpha);
            applyColor(currentColor, withAlpha, newColor);
          }
        }

        private void applyColor(Color currentColor, boolean withAlpha, Color newColor) {
          if (newColor == null || newColor.equals(currentColor)) return;

          String newColorHex = "#" + ColorUtil.toHex(newColor, withAlpha);
          Project project = myLiteral.getProject();
          JsonStringLiteral newLiteral = new JsonElementGenerator(project).createStringLiteral(newColorHex);

          WriteCommandAction.writeCommandAction(project, myLiteral.getContainingFile()).run(
            () -> myLiteral = (JsonStringLiteral)myLiteral.replace(newLiteral)
          );
        }
      };
    }

    private boolean canChooseColor() {
      return isColorCode(myColorText);
    }

    @Nullable
    private Color getColor(@NotNull String colorText) {
      if (!isColorCode(colorText)) {
        return findNamedColor(colorText);
      }

      return parseColor(colorText);
    }

    @Nullable
    private static Color parseColor(@NotNull String colorHex) {
      boolean isRgba = isRgbaColorHex(colorHex);
      if (!isRgba && !isRgbColorHex(colorHex)) return null;

      try {
        String alpha = isRgba ? colorHex.substring(HEX_COLOR_LENGTH_RGB) : null;
        String colorHexWithoutAlpha = isRgba ? colorHex.substring(0, HEX_COLOR_LENGTH_RGB) : colorHex;
        Color color = Color.decode(colorHexWithoutAlpha);
        if (isRgba) {
          color = ColorUtil.toAlpha(color, Integer.parseInt(alpha, 16));
        }

        return color;
      }
      catch (NumberFormatException ignored) {
        return null;
      }
    }

    @Nullable
    private Color findNamedColor(String colorText) {
      final PsiFile file = myLiteral.getContainingFile();
      if (!(file instanceof JsonFile)) return null;
      final List<JsonProperty> colors = ThemeJsonUtil.getNamedColors((JsonFile)file);
      final JsonProperty namedColor = ContainerUtil.find(colors, property -> property.getName().equals(colorText));
      if (namedColor == null) return null;

      final JsonValue value = namedColor.getValue();
      if (!(value instanceof JsonStringLiteral)) return null;
      return parseColor(((JsonStringLiteral)value).getValue());
    }

    private static boolean isRgbaColorHex(@NotNull String colorHex) {
      return colorHex.length() == HEX_COLOR_LENGTH_RGBA;
    }

    private static boolean isRgbColorHex(@NotNull String colorHex) {
      return colorHex.length() == HEX_COLOR_LENGTH_RGB;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MyRenderer renderer = (MyRenderer)o;
      return myColorText.equals(renderer.myColorText) &&
             myLiteral.equals(renderer.myLiteral);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myColorText, myLiteral);
    }
  }
}
