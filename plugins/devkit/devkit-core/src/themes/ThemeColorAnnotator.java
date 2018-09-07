// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.project.DumbAware;
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

public class ThemeColorAnnotator implements Annotator, DumbAware {
  private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#([A-Fa-f0-9]{6})$");
  private static final ColorLineMarkerProvider COLOR_LINE_MARKER_PROVIDER = new ColorLineMarkerProvider();


  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (!isColorLineMarkerProviderEnabled() || !isTargetElement(element)) return;

    Annotation annotation = holder.createInfoAnnotation(element, null);
    JsonStringLiteral literal = (JsonStringLiteral)element;
    annotation.setGutterIconRenderer(new MyRenderer(literal.getValue(), literal));
  }

  private static boolean isColorLineMarkerProviderEnabled() {
    return LineMarkerSettings.getSettings().isEnabled(COLOR_LINE_MARKER_PROVIDER);
  }

  private static boolean isTargetElement(@NotNull PsiElement element) {
    if (!(element instanceof JsonStringLiteral)) return false;
    if (!ThemeJsonSchemaProviderFactory.isAllowedFileName(element.getContainingFile().getName())) return false;

    String text = ((JsonStringLiteral)element).getValue();
    if (!StringUtil.startsWithChar(text, '#')) return false;
    if (text.length() != 7) return false; // '#FFFFFF'
    if (!HEX_COLOR_PATTERN.matcher(text).matches()) return false;

    return true;
  }


  private static class MyRenderer extends GutterIconRenderer {
    private static final int ICON_SIZE = 8;

    private final String myColorHex;
    private final JsonStringLiteral myLiteral;


    private MyRenderer(@NotNull String colorHex, @NotNull JsonStringLiteral literal) {
      myColorHex = colorHex;
      myLiteral = literal;
    }

    @NotNull
    @Override
    public Icon getIcon() {
      try {
        Color color = Color.decode(myColorHex);
        return JBUI.scale(new ColorIcon(ICON_SIZE, color));
      } catch (NumberFormatException ignore) {
        return JBUI.scale(EmptyIcon.create(ICON_SIZE));
      }
    }

    @Nullable
    @Override
    public AnAction getClickAction() {
      return new AnAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
          if (editor == null) return;

          Color currentColor = getColor(myColorHex);
          if (currentColor == null) return;

          Color newColor = ColorChooser.chooseColor(editor.getComponent(),
                                                    DevKitBundle.message("theme.choose.color.dialog.title"),
                                                    currentColor);
          if (newColor == null || newColor.equals(currentColor)) return;

          String newColorHex = ColorUtil.toHtmlColor(newColor);
          Project project = myLiteral.getProject();
          JsonStringLiteral newLiteral = new JsonElementGenerator(project).createStringLiteral(newColorHex);

          WriteCommandAction.writeCommandAction(project, myLiteral.getContainingFile()).run(() -> myLiteral.replace(newLiteral));
        }
      };
    }

    @Nullable
    private static Color getColor(@NotNull String colorHex) {
      try {
        return Color.decode(colorHex);
      }
      catch (NumberFormatException ignored) {
        return null;
      }
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
