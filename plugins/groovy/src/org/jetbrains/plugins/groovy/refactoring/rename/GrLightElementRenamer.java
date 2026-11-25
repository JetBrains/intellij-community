// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.modplan.ModPlanAnalysisResult;
import com.intellij.refactoring.modplan.ModPlanConflicts;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.rename.ModPlanBridgeRenameProvider;
import com.intellij.refactoring.modplan.rename.ModPlanRenameUtil;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrRenameableLightElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

import java.util.List;
import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
public final class GrLightElementRenamer extends RenamePsiElementProcessor {
  @Override
  public PsiElement substituteElementToRename(@NotNull PsiElement element, @Nullable Editor editor) {
    String message =
      RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("rename.is.not.applicable.to.implicit.elements"));
    CommonRefactoringUtil.showErrorHint(element.getProject(), editor, message, RefactoringBundle.message("rename.title"), null);
    return null;
  }

  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    if (!(element instanceof LightElement)) return false;
    if (element instanceof GrRenameableLightElement) return false;
    final Language language = element.getLanguage();
    return GroovyLanguage.INSTANCE.equals(language);
  }

  /**
   * The rename of an implicit Groovy element on the ModPlan engine with this processor. The user cannot rename it, as
   * {@link #substituteElementToRename} refuses. The class of a script is renamed with its file.
   */
  @ApiStatus.Internal
  public static final class ModPlanGrLightElementRenameProvider extends ModPlanBridgeRenameProvider {
    public ModPlanGrLightElementRenameProvider() {
      super(GrLightElementRenamer.class);
    }



    /**
     * Refuses the rename, as {@link #substituteElementToRename} does, but with no error hint: the engine has no editor. The default
     * substitution would call {@link #substituteElementToRename}, which shows the hint.
     * <pre>{@code
     * class Bean {
     *   def hits   // has the implicit getter getHits, a light element
     * }
     * // rename getHits to getCount: refused, NOT_APPLICABLE
     * }</pre>
     */
    @Override
    protected @Nullable PsiElement substitute(@NotNull PsiElement element, @NotNull ModPlanOptionValues answers) {
      return null;
    }

    /**
     * Refuses every implicit element except a script class. Such an element has no text of its own in a file, so no edit can write it.
     * It comes here with no substitution, so {@link #substitute} cannot refuse it, and this is the first hook of the provider that sees
     * it. There are two such paths:
     * <ul>
     *   <li>A caller that does not substitute, see {@code ModPlanRenameRefactoring.request}: the rename of the implicit getter
     *   {@code getHits} of {@code class Bean { def hits }} goes directly to the analysis.</li>
     *   <li>A related rename that another provider adds.</li>
     * </ul>
     * A script class comes as a related rename of its file, see {@link RenameGroovyScriptProcessor#prepareRenaming}. Then the move of the
     * file renames the class, and the file provider gives that move. So the analysis removes its own move of the same file: two moves of one
     * file would fail.
     * <pre>{@code
     * // runner.groovy
     * println 'hi'
     * // User.groovy
     * class User { def start() { new runner().run() } }
     * // rename runner.groovy to launcher.groovy: the run renames the file and the script class runner -> launcher
     * // launcher.groovy, moved once by the file provider
     * class User { def start() { new launcher().run() } }
     * }</pre>
     */
    @Override
    protected @NotNull ModPlanAnalysisResult analyze(@NotNull PsiElement element,
                                                     @NotNull String newName,
                                                     @NotNull List<UsageInfo> usages,
                                                     @NotNull Map<PsiElement, String> renamed) {
      if (!(element instanceof GroovyScriptClass scriptClass)) {
        return new ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE);
      }
      ModPlanAnalysisResult result = super.analyze(element, newName, usages, renamed);
      PsiFile file = scriptClass.getContainingFile();
      if (!renamed.containsKey(file) || !(result instanceof ModPlanAnalysisResult.Found(List<ModPlanEdit> edits, ModPlanConflicts conflicts))) {
        return result;
      }
      VirtualFile virtualFile = file.getVirtualFile();
      List<ModPlanEdit> withoutMove =
        ContainerUtil.filter(edits, edit -> !(edit instanceof ModPlanEdit.Move move && move.file().equals(virtualFile)));
      return new ModPlanAnalysisResult.Found(withoutMove, conflicts);
    }

    /**
     * As {@link GroovyScriptClass#setName}: the file of the script gets the new name with its extension. The default edit would call
     * {@code setName} on the copy of the class, but a light class has no copy in the copy of the file. A caller that does not substitute
     * can rename the script class itself:
     * <pre>{@code
     * // runner.groovy
     * println 'hi'
     * // rename the script class runner to launcher: the edit moves runner.groovy to launcher.groovy
     * }</pre>
     */
    @Override
    protected @NotNull List<ModPlanEdit> declarationEdits(@NotNull PsiElement element, @NotNull String newName) {
      if (!(element instanceof GroovyScriptClass scriptClass)) return super.declarationEdits(element, newName);
      PsiFile file = scriptClass.getContainingFile();
      String extension = file.getViewProvider().getVirtualFile().getExtension();
      return List.of(ModPlanRenameUtil.declarationEdit(file, PathUtil.makeFileName(newName, extension)));
    }
  }
}
