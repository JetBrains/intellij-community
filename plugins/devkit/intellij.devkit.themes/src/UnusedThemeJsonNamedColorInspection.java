// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.json.psi.JsonElementVisitor;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonProperty;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

import static org.jetbrains.idea.devkit.themes.ThemeJsonUtil.getParentNamedColorsMap;
import static org.jetbrains.idea.devkit.themes.ThemeJsonUtil.isInsideColors;
import static org.jetbrains.idea.devkit.themes.ThemeJsonUtil.isThemeFilename;

@ApiStatus.Internal
public final class UnusedThemeJsonNamedColorInspection extends LocalInspectionTool {

  private static final Collection<String> COLOR_PALETTE_KEY_PREFIXES =
    List.of("gray-", "blue-", "green-", "orange-", "pink-", "purple-", "red-", "yellow-", "teal-", "magenta-");

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    PsiFile file = holder.getFile();
    if (!isThemeFilename(file.getName())) return PsiElementVisitor.EMPTY_VISITOR;

    return new JsonElementVisitor() {
      @Override
      public void visitProperty(@NotNull JsonProperty property) {
        if (!isInsideColors(property)) return;

        var nameElement = property.getNameElement();
        var name = property.getName();

        if (ContainerUtil.exists(COLOR_PALETTE_KEY_PREFIXES, name::startsWith)) return;

        Query<PsiReference> query = ReferencesSearch.search(property, new LocalSearchScope(file));
        if (query.findFirst() == null) {
          if (getParentNamedColorsMap((JsonFile)file).containsKey(name)) return;

          holder.registerProblem(nameElement,
                                 DevKitThemesBundle.message("inspection.unused.theme.json.named.color.message", name),
                                 ProblemHighlightType.LIKE_UNUSED_SYMBOL);
        }
      }
    };
  }
}
