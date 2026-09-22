// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.RefactoringUiService;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

/**
 * <h3>Lifecycle</h3>
 * <p>
 * A simple implementation of this class might have the following lifecycle:
 *
 * <ol>
 *   <li>{@link #canProcessElement(PsiElement) canProcessElement()} is called to determine if this RenameProcessor can be used to rename the element</li>
 *   <li>If yes, {@link #findReferences findReferences()} is called with on that element to find references to it that also should be renamed
 *   (it is what {@code usages} are in the next call)
 *   </li>
 *   <li>{@link #renameElement(PsiElement element, String newName, UsageInfo[] usages, RefactoringElementListener) renameElement()} is called,
 *   with the new step name entered in the rename dialog and a list of usages.
 * <p>
 *   This method must rename the element and all its usages. Should not perform duplicate work that findReferences has already done.
 *   </li>
 * </ol>
 * <p>
 * The class follows the <i>Chain of Responsibility</i> design pattern - when a rename operation is requested, the platform iterates through
 * all registered processors and uses the first one that returns true from {@link #canProcessElement canProcessElement()}.
 *
 * @see RenameHandler
 * @see RenameProcessor
 */
public abstract class RenamePsiElementProcessorBase implements RenamePsiElementProcessorCore {
  public static final ExtensionPointName<RenamePsiElementProcessorBase> EP_NAME =
    ExtensionPointName.create("com.intellij.renamePsiElementProcessor");

  public abstract boolean canProcessElement(@NotNull PsiElement element);

  @Override
  public void renameElement(@NotNull PsiElement element,
                            @NotNull String newName,
                            UsageInfo @NotNull [] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    RenamePsiElementProcessorCore.super.renameElement(element, newName, usages, listener);
  }

  @Override
  public @NotNull @Unmodifiable Collection<PsiReference> findReferences(@NotNull PsiElement element,
                                                                        @NotNull SearchScope searchScope,
                                                                        boolean searchInCommentsAndStrings) {
    return RenamePsiElementProcessorCore.super.findReferences(element, searchScope, searchInCommentsAndStrings);
  }

  @Override
  public @Nullable TextOccurrenceSearchStrings getTextOccurrenceSearchStrings(@NotNull PsiElement element, @NotNull String newName) {
    return RenamePsiElementProcessorCore.super.getTextOccurrenceSearchStrings(element, newName);
  }

  @Override
  public @Nullable String getQualifiedNameAfterRename(@NotNull PsiElement element, @NotNull String newName, final boolean nonJava) {
    return RenamePsiElementProcessorCore.super.getQualifiedNameAfterRename(element, newName, nonJava);
  }

  @Override
  public @Nullable Runnable getPostRenameCallback(@NotNull PsiElement element,
                                                  @NotNull String newName,
                                                  @NotNull RefactoringElementListener elementListener) {
    return RenamePsiElementProcessorCore.super.getPostRenameCallback(element, newName, elementListener);
  }

  @Override
  public @Nullable Runnable getPostRenameCallback(@NotNull PsiElement element,
                                                  @NotNull String newName,
                                                  @NotNull Collection<UsageInfo> usages,
                                                  @NotNull Map<PsiElement, String> allRenames,
                                                  @NotNull RefactoringElementListener elementListener) {
    return RenamePsiElementProcessorCore.super.getPostRenameCallback(element, newName, usages, allRenames, elementListener);
  }

  @Override
  public void findCollisions(@NotNull PsiElement element,
                             @NotNull String newName,
                             @NotNull Map<? extends PsiElement, String> allRenames,
                             @NotNull List<UsageInfo> result) {
    RenamePsiElementProcessorCore.super.findCollisions(element, newName, allRenames, result);
  }

  @Override
  public @Nullable PsiElement getElementToSearchInStringsAndComments(@NotNull PsiElement element) {
    return RenamePsiElementProcessorCore.super.getElementToSearchInStringsAndComments(element);
  }

  @Override
  public @NotNull UsageInfo createUsageInfo(@NotNull PsiElement element, @NotNull PsiReference ref, @NotNull PsiElement referenceElement) {
    return RenamePsiElementProcessorCore.super.createUsageInfo(element, ref, referenceElement);
  }

  public RenameRefactoringDialog createDialog(@NotNull Project project,
                                              @NotNull PsiElement element,
                                              @Nullable PsiElement nameSuggestionContext,
                                              @Nullable Editor editor) {
    for (RenameRefactoringDialogProvider dialogProvider : RenameRefactoringDialogProvider.EP_NAME.getExtensionList()) {
      if (dialogProvider.isApplicable(this)) {
        return dialogProvider.createDialog(project, element, nameSuggestionContext, editor);
      }
    }

    return RefactoringUiService.getInstance().createRenameRefactoringDialog(project, element, nameSuggestionContext, editor);
  }

  /** @deprecated use {@link RenamePsiElementProcessor#findReferences(PsiElement, SearchScope, boolean)} instead */
  @Deprecated
  public @NotNull @Unmodifiable Collection<PsiReference> findReferences(@NotNull PsiElement element, boolean searchInCommentsAndStrings) {
    return findReferences(element, GlobalSearchScope.projectScope(element.getProject()), searchInCommentsAndStrings);
  }

  /** @deprecated use {@link RenamePsiElementProcessor#findReferences(PsiElement, SearchScope, boolean)} instead */
  @Deprecated
  public @NotNull @Unmodifiable Collection<PsiReference> findReferences(@NotNull PsiElement element) {
    return findReferences(element, GlobalSearchScope.projectScope(element.getProject()), false);
  }

  /**
   * Builds the complete set of elements to be renamed during the refactoring.
   * <p>
   * Expected to be called from EDT.
   *
   * @param element    the base element for the refactoring.
   * @param newName    the name into which the element is being renamed.
   * @param allRenames the map (from element to its new name) into which all additional elements to be renamed should be stored.
   */
  public void prepareRenaming(@NotNull PsiElement element, @NotNull String newName, @NotNull Map<PsiElement, String> allRenames) {
    prepareRenaming(element, newName, allRenames, PsiSearchHelper.getInstance(element.getProject()).getUseScope(element));
  }

  public void prepareRenaming(@NotNull PsiElement element,
                              @NotNull String newName,
                              @NotNull Map<PsiElement, String> allRenames,
                              @NotNull SearchScope scope) {
  }

  public void findExistingNameConflicts(@NotNull PsiElement element,
                                        @NotNull String newName,
                                        @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
  }

  /**
   * Entry point for finding conflicts.
   *
   * @param element    primary element being renamed
   * @param newName    new name of the primary element
   * @param conflicts  map to put conflicts
   * @param allRenames other elements being renamed with their new names; not expected to be modified
   */
  public void findExistingNameConflicts(@NotNull PsiElement element,
                                        @NotNull String newName,
                                        @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts,
                                        @NotNull Map<PsiElement, String> allRenames) {
    findExistingNameConflicts(element, newName, conflicts);
  }

  public boolean isInplaceRenameSupported() {
    return true;
  }

  public static @NotNull RenamePsiElementProcessorBase forPsiElement(@NotNull PsiElement element) {
    DumbService dumbService = DumbService.getInstance(element.getProject());
    for (RenamePsiElementProcessorBase processor : EP_NAME.getExtensionList()) {
      if (dumbService.isUsableInCurrentContext(processor) && processor.canProcessElement(element)) {
        return processor;
      }
    }
    return DEFAULT;
  }

  public @Nullable @NonNls String getHelpID(final PsiElement element) {
    if (element instanceof PsiFile) {
      return "refactoring.renameFile";
    }
    return "refactoring.renameDialogs";
  }

  public boolean isToSearchInComments(@NotNull PsiElement element) {
    return element instanceof PsiFileSystemItem && RefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FILE;
  }

  public void setToSearchInComments(@NotNull PsiElement element, boolean enabled) {
    if (element instanceof PsiFileSystemItem) {
      RefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FILE = enabled;
    }
  }

  public boolean isToSearchForTextOccurrences(@NotNull PsiElement element) {
    return element instanceof PsiFileSystemItem && RefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_FILE;
  }

  public void setToSearchForTextOccurrences(@NotNull PsiElement element, boolean enabled) {
    if (element instanceof PsiFileSystemItem) {
      RefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_FILE = enabled;
    }
  }

  public boolean showRenamePreviewButton(@NotNull PsiElement psiElement) {
    return true;
  }

  /**
   * Returns the element to be renamed instead of the element on which the rename refactoring was invoked (for example, a super method
   * of an inherited method).
   *
   * @param element the element on which the refactoring was invoked.
   * @param editor  the editor in which the refactoring was invoked.
   * @return the element to rename, or null if the rename refactoring should be canceled.
   */
  public @Nullable PsiElement substituteElementToRename(@NotNull PsiElement element, @Nullable Editor editor) {
    return element;
  }

  /**
   * Substitutes the element to be renamed and initiates the rename procedure.
   * Should be used to prevent modal dialogs from appearing during inplace rename.
   *
   * @param element        the element on which refactoring was invoked
   * @param editor         the editor in which inplace refactoring was invoked
   * @param renameCallback the rename callback to be called on the chosen substitution
   */
  public void substituteElementToRename(final @NotNull PsiElement element,
                                        @NotNull Editor editor,
                                        @NotNull Pass<? super PsiElement> renameCallback) {
    final PsiElement psiElement = substituteElementToRename(element, editor);
    if (psiElement == null) return;
    if (!PsiElementRenameHandler.canRename(psiElement.getProject(), editor, psiElement)) return;
    renameCallback.accept(psiElement);
  }

  /**
   * Use this method to force showing preview for custom processors.
   * This method is always called after prepareRenaming()
   *
   * @return force show preview
   */
  public boolean forcesShowPreview() {
    return false;
  }

  public interface DefaultRenamePsiElementProcessor {
  }

  private static class MyRenamePsiElementProcessorBase extends RenamePsiElementProcessorBase implements DefaultRenamePsiElementProcessor {
    @Override
    public boolean canProcessElement(final @NotNull PsiElement element) {
      return true;
    }
  }

  public static final RenamePsiElementProcessorBase DEFAULT = new MyRenamePsiElementProcessorBase();
}
