// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.refactoring.modplan.rename.ModPlanBridgeRenameProvider;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.List;
import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
public final class RenameGroovyPropertyProcessor extends RenamePsiElementProcessor {
  Logger LOG = Logger.getInstance(RenameGroovyPropertyProcessor.class);

  @Override
  public void prepareRenaming(@NotNull PsiElement element, @NotNull String newName, @NotNull Map<PsiElement, String> allRenames) {
    LOG.assertTrue(element instanceof PropertyForRename);
    final List<? extends PsiElement> elementsToRename = ((PropertyForRename)element).getElementsToRename();
    for (PsiElement psiElement : elementsToRename) {
      if (psiElement instanceof GrField) {
        allRenames.put(psiElement, newName);
      }
      else if (psiElement instanceof GrMethod) {
        if (GroovyPropertyUtils.isSimplePropertyGetter((PsiMethod)psiElement)) {
          allRenames.put(psiElement, RenamePropertyUtil.getGetterNameByOldName(newName, ((PsiMethod)psiElement).getName()));
        }
        else {
          allRenames.put(psiElement, GroovyPropertyUtils.getSetterName(newName));
        }
      }
    }
    allRenames.remove(element);
  }

  @Override
  public void renameElement(@NotNull PsiElement element, @NotNull String newName, UsageInfo @NotNull [] usages, @Nullable RefactoringElementListener listener)
    throws IncorrectOperationException {

    //do nothing
  }

  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof PropertyForRename;
  }

  /**
   * The rename of a property on the ModPlan engine with this processor. {@link #prepareRenaming} renames the field and the accessors of
   * the property, so the property itself has no usages and no declaration, as {@link #renameElement} writes nothing.
   */
  @ApiStatus.Internal
  public static final class ModPlanGroovyPropertyRenameProvider extends ModPlanBridgeRenameProvider {
    public ModPlanGroovyPropertyRenameProvider() {
      super(RenameGroovyPropertyProcessor.class);
    }

    /**
     * No usages: a {@link PropertyForRename} is a fake element, and nothing refers to it. The field and the accessors that
     * {@link #prepareRenaming} adds have the usages, and their providers rename them.
     * <pre>{@code
     * class Bean {
     *   def hits
     *   def getHits() { hits }
     *   void setHits(def value) { hits = value }
     * }
     * // rename the property hits to count: prepareRenaming adds hits -> count, getHits -> getCount, setHits -> setCount
     * class Bean {
     *   def count
     *   def getCount() { count }
     *   void setCount(def value) { count = value }
     * }
     * }</pre>
     */
    @Override
    protected @NotNull List<UsageInfo> usagesOf(@NotNull PsiElement element,
                                              @NotNull String newName,
                                              @NotNull ModPlanRequest request,
                                              @NotNull Map<PsiElement, String> renamed) {
      return List.of();
    }

    /**
     * No edit, as {@link #renameElement} writes nothing: a {@link PropertyForRename} is in no file. In the example of {@link #usagesOf}, the
     * edits of the field {@code hits} and of the accessors write every name. The property itself has no text to write.
     */
    @Override
    protected @NotNull List<ModPlanEdit> declarationEdits(@NotNull PsiElement element, @NotNull String newName) {
      return List.of();
    }
  }
}
