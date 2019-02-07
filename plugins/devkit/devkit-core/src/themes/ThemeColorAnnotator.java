// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes;

import com.intellij.codeInsight.daemon.LineMarkerSettings;
import com.intellij.json.psi.JsonElementGenerator;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.ColorChooser;
import com.intellij.ui.ColorLineMarkerProvider;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.regex.Pattern;

public class ThemeColorAnnotator implements Annotator {
  private static final Pattern COLOR_HEX_PATTERN_RGB = Pattern.compile("^#([A-Fa-f0-9]{6})$");
  private static final Pattern COLOR_HEX_PATTERN_RGBA = Pattern.compile("^#([A-Fa-f0-9]{8})$");
  private static final int HEX_COLOR_LENGTH_RGB = 7;
  private static final int HEX_COLOR_LENGTH_RGBA = 9;


  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (!isColorLineMarkerProviderEnabled() || !isTargetElement(element)) return;

    Annotation annotation = holder.createInfoAnnotation(element, null);
    JsonStringLiteral literal = (JsonStringLiteral)element;
    annotation.setGutterIconRenderer(new MyRenderer(literal.getValue(), literal));
  }

  private static boolean isColorLineMarkerProviderEnabled() {
    return LineMarkerSettings.getSettings().isEnabled(ColorLineMarkerProvider.INSTANCE);
  }

  static boolean isTargetElement(@NotNull PsiElement element) {
    if (!(element instanceof JsonStringLiteral)) return false;
    if (!ThemeJsonSchemaProviderFactory.isAllowedFileName(element.getContainingFile().getName())) return false;

    String text = ((JsonStringLiteral)element).getValue();
    return isColorCode(text);
  }

  private static boolean isColorCode(@Nullable String text) {
    if (!StringUtil.startsWithChar(text, '#')) return false;
    //noinspection ConstantConditions - StringUtil#startsWithChar checks for null
    if (text.length() != HEX_COLOR_LENGTH_RGB && text.length() != HEX_COLOR_LENGTH_RGBA) return false;
    return COLOR_HEX_PATTERN_RGB.matcher(text).matches() || COLOR_HEX_PATTERN_RGBA.matcher(text).matches();
  }


  private static class MyRenderer extends GutterIconRenderer {
    private static final int ICON_SIZE = 12;

    private final String myColorHex;
    private final JsonStringLiteral myLiteral;


    private MyRenderer(@NotNull String colorHex, @NotNull JsonStringLiteral literal) {
      myColorHex = colorHex;
      myLiteral = literal;
    }

    @NotNull
    @Override
    public Icon getIcon() {
      Color color = getColor(myColorHex);
      if (color != null) {
        return JBUI.scale(new ColorIcon(ICON_SIZE, color));
      }
      return JBUI.scale(EmptyIcon.create(ICON_SIZE));
    }

    @Override
    public boolean isNavigateAction() {
      return true;
    }

    @Nullable
    @Override
    public String getTooltipText() {
      return "Choose Color";
    }

    @Nullable
    @Override
    public AnAction getClickAction() {
      return new AnAction("Choose Color...") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
          if (editor == null) return;

          Color currentColor = getColor(myColorHex);
          if (currentColor == null) return;

          boolean withAlpha = isRgbaColorHex(myColorHex);
          Color newColor = ColorChooser.chooseColor(editor.getProject(),
                                                    editor.getComponent(),
                                                    DevKitBundle.message("theme.choose.color.dialog.title"),
                                                    currentColor,
                                                    withAlpha);
          if (newColor == null || newColor.equals(currentColor)) return;

          String newColorHex = "#" + ColorUtil.toHex(newColor, withAlpha);
          Project project = myLiteral.getProject();
          JsonStringLiteral newLiteral = new JsonElementGenerator(project).createStringLiteral(newColorHex);

          WriteCommandAction.writeCommandAction(project, myLiteral.getContainingFile()).run(() -> myLiteral.replace(newLiteral));
        }
      };
    }

    @Nullable
    private static Color getColor(@NotNull String colorHex) {
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
      return myColorHex.equals(renderer.myColorHex) &&
             myLiteral.equals(renderer.myLiteral);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myColorHex, myLiteral);
    }
  }
}
