// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.themes;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.execution.lineMarker.RunLineMarkerProvider;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UITheme;
import com.intellij.ide.ui.laf.UIThemeBasedLookAndFeelInfo;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Konstantin Bulenkov
 */
public class ApplyThemeLineMarkerProvider extends RunLineMarkerProvider {
  @NotNull
  @Override
  public String getName() {
    return "Apply theme";
  }

  @Nullable
  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement psiElement) {
    String name = psiElement.getContainingFile().getName();
    if (!name.endsWith(".theme.json")) {
      return null;
    }
    PsiElement element = psiElement.getParent();
    if (element instanceof JsonStringLiteral && element.getText().equals("\"name\"")) {
      return new LineMarkerInfo<>(psiElement, element.getTextRange(), getIcon(),

                                  psi -> {
                                    PsiElement parent = psi.getParent().getParent();
                                    if (parent instanceof JsonProperty) {
                                      JsonValue value = ((JsonProperty)parent).getValue();
                                      if (value != null) {
                                        return "Apply '" + value.getText() + "' theme";
                                      }
                                    }
                                    return getName();
                                  },

                                  (e, psi) -> applyTempTheme(psi.getContainingFile()),
                                  GutterIconRenderer.Alignment.RIGHT);
    }
    return null;
  }

  private static void applyTempTheme(@NotNull PsiFile json) {
    try {
      FileDocumentManager.getInstance().saveAllDocuments();
      UITheme theme = UITheme.loadFromJson(json.getVirtualFile().getInputStream(), "Temp theme", null);
      LafManager.getInstance().setCurrentLookAndFeel(new UIThemeBasedLookAndFeelInfo(theme));
      LafManager.getInstance().updateUI();
    }
    catch (IOException ignore) {}
  }
}
