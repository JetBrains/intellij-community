// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.modplan.ModPlanAnalysisResult;
import com.intellij.refactoring.modplan.ModPlanConflicts;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.rename.ModPlanBridgeRenameProvider;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenameUtilBase;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.transformations.inline.GroovyInlineASTTransformationPerformer;
import org.jetbrains.plugins.groovy.transformations.inline.GroovyInlineTransformationUtilKt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GrInlineTransformationElementRenamer extends RenamePsiElementProcessor {

  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    final Language language = element.getLanguage();
    if (!GroovyLanguage.INSTANCE.equals(language)) {
      return false;
    }
    GroovyInlineASTTransformationPerformer performer = GroovyInlineTransformationUtilKt.getHierarchicalInlineTransformationPerformer(element);
    return performer != null && performer.computeStaticReference(element) != null;
  }

  @Override
  public void renameElement(@NotNull PsiElement element,
                            @NotNull String newName,
                            UsageInfo @NotNull [] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    for (UsageInfo usage : usages) {
      RenameUtilBase.rename(usage, newName);
    }
    if (listener != null) {
      listener.elementRenamed(element);
    }
  }

  /**
   * The rename on the ModPlan engine of an element of an inline transformation, such as an alias of a GINQ data source. The element has no
   * declaration of its own, as {@link #renameElement} renames only the usages.
   */
  @ApiStatus.Internal
  public static final class ModPlanGrInlineTransformationRenameProvider extends ModPlanBridgeRenameProvider {
    public ModPlanGrInlineTransformationRenameProvider() {
      super(GrInlineTransformationElementRenamer.class);
    }

    /**
     * The edits of every reference, as {@link #renameElement} renames every usage, also the one at the element itself. The default
     * analysis has two problems:
     * <ul>
     *   <li>It skips the usage at the element itself, which is in {@code renamed}.</li>
     *   <li>It renames the reference in the copy of the file, but the copy has no such reference, because the searcher of the transformation
     *   makes it. The manipulator of the element of the reference writes the new name instead.</li>
     * </ul>
     * <pre>{@code
     * GQ { from n in [1, 2, 3] select n }
     * // rename n to m
     * GQ { from m in [1, 2, 3] select m }
     * }</pre>
     */
    @Override
    protected @NotNull ModPlanAnalysisResult analyze(@NotNull PsiElement element,
                                                     @NotNull String newName,
                                                     @NotNull List<UsageInfo> usages,
                                                     @NotNull Map<PsiElement, String> renamed) {
      List<ModPlanEdit> referenceEdits = new ArrayList<>();
      List<UsageInfo> otherUsages = new ArrayList<>();
      for (UsageInfo usage : usages) {
        PsiReference reference = usage.getReference();
        if (reference == null || usage instanceof NonCodeUsageInfo || usage instanceof UnresolvableCollisionUsageInfo) {
          otherUsages.add(usage);
          continue;
        }
        TextRange range = reference.getRangeInElement();
        referenceEdits.add(ModPlanEdit.update(reference.getElement(), copy -> ElementManipulators.handleContentChange(copy, range, newName)));
      }
      ModPlanAnalysisResult result = super.analyze(element, newName, otherUsages, renamed);
      if (!(result instanceof ModPlanAnalysisResult.Found(List<ModPlanEdit> edits, ModPlanConflicts conflicts))) return result;
      return new ModPlanAnalysisResult.Found(ContainerUtil.concat(referenceEdits, edits), conflicts);
    }

    /**
     * No edit: the element {@code n} of {@code from n in [1, 2, 3]} is also a usage, and {@link #analyze} gives it the edit of a usage. A
     * declaration edit would write the name a second time.
     */
    @Override
    protected @NotNull List<ModPlanEdit> declarationEdits(@NotNull PsiElement element, @NotNull String newName) {
      return List.of();
    }
  }
}
