// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.ui.UIThemeMetadata;
import com.intellij.json.psi.JsonElementVisitor;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class UnresolvedThemeKeyInspection extends LocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!ThemeJsonUtil.isThemeFilename(holder.getFile().getName())) return PsiElementVisitor.EMPTY_VISITOR;

    return new JsonElementVisitor() {
      @Override
      public void visitProperty(@NotNull JsonProperty property) {
        if (property.getValue() instanceof JsonObject) return; // do not check intermediary keys

        if (!ThemeJsonUtil.isInsideUiProperty(property)) return;

        Pair<UIThemeMetadata, UIThemeMetadata.UIKeyMetadata> pair = ThemeJsonUtil.findMetadata(property);
        if (pair == null) {
          String parentNames = ThemeJsonUtil.getParentNames(property);
          if (parentNames.startsWith("*")) return; // anything allowed

          String fullKey = parentNames.isEmpty() ? property.getName() : parentNames + "." + property.getName();
          holder.registerProblem(property.getNameElement(),
                                 DevKitThemesBundle.message("theme.highlighting.unresolved.key", fullKey),
                                 ProblemHighlightType.WARNING);
          return;
        }

        if (pair.second.isDeprecated()) {
          holder.registerProblem(property.getNameElement(),
                                 DevKitThemesBundle.message("theme.highlighting.deprecated.key", pair.second.getKey()),
                                 ProblemHighlightType.LIKE_DEPRECATED);
        }
      }
    };
  }
}
