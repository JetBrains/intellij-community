// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.ide.ui.UIThemeMetadata;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

final class ThemeAnnotator implements Annotator, DumbAware {

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (!(element instanceof JsonProperty property)) return;
    if (!ThemeJsonUtil.isThemeFilename(holder.getCurrentAnnotationSession().getFile().getName())) return;

    if (property.getValue() instanceof JsonObject) return;  // do not check intermediary keys

    if (!ThemeJsonUtil.isInsideUiProperty(property)) return;

    final Pair<UIThemeMetadata, UIThemeMetadata.UIKeyMetadata> pair = ThemeJsonUtil.findMetadata(property);
    if (pair == null) {
      String parentNames = ThemeJsonUtil.getParentNames(property);
      if (parentNames.startsWith("*")) return; // anything allowed

      String fullKey = parentNames.isEmpty() ? property.getName() : parentNames + "." + property.getName();
      holder.newAnnotation(HighlightSeverity.WARNING,
                           DevKitThemesBundle.message("theme.highlighting.unresolved.key", fullKey))
        .range(property.getNameElement())
        .highlightType(ProblemHighlightType.WARNING).create();
      return;
    }

    if (pair.second.isDeprecated()) {
      holder.newAnnotation(HighlightSeverity.WARNING,
                           DevKitThemesBundle.message("theme.highlighting.deprecated.key", pair.second.getKey()))
        .range(property.getNameElement())
        .highlightType(ProblemHighlightType.LIKE_DEPRECATED).create();
    }
  }
}
