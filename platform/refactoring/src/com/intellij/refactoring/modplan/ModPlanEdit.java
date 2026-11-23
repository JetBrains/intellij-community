// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan;

import com.intellij.modcommand.FutureVirtualFile;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * An edit of a ModPlan refactoring. A language describes it during the analysis; the engine applies it to non-physical copies,
 * so nothing can ask the user or show UI.
 * <p>
 * An update keeps the elements it changes as smart pointers, so it outlives a read action. Build it with {@link #update}, {@link #delete}
 * or with a pointer to the element. The engine turns an edit with a gone element into no command, which refuses the refactoring.
 * <p>
 * <b>Which edit to use.</b>
 * <ul>
 *   <li>{@link Update}: the edit changes an element as a whole, such as a declaration, an import or a statement. The address is the
 *   element. If another edit removes or replaces the element, the update is skipped.</li>
 *   <li>{@link UpdateRange}: the edit changes what is at a place of the text, such as a reference, and another edit may move the place
 *   or build the PSI around it again. The address is the range. The action finds its PSI at the range at the time of the write.</li>
 *   <li>{@link ReplaceText}: the place is not code, such as a comment, a string or a text file. The engine replaces the text after
 *   every PSI edit.</li>
 * </ul>
 */
public sealed interface ModPlanEdit {
  /**
   * Changes elements, in one file or in several. The engine maps all the elements of all edits to their writable copies first, so an
   * edit that changes several elements of one file passes them all, and does not split into one update per element. The action gets no
   * updater, so it cannot reach an element that is not passed. If another edit removed any of the elements before, the update is skipped.
   * <p>
   * The elements of one file share one copy. A reference in a copy resolves only into its own copy, so the action must not compare with
   * what a reference resolves to: resolve in advance and pass the found elements.
   *
   * @param pointers the pointers to the elements to change
   * @param action   gets the writable copies of the elements, in the same order
   */
  record Update(@NotNull @Unmodifiable List<SmartPsiElementPointer<PsiElement>> pointers,
                @NotNull Consumer<? super List<PsiElement>> action) implements ModPlanEdit {
    public Update {
      if (pointers.isEmpty()) {
        throw new IllegalArgumentException("An update needs an element");
      }
      pointers = List.copyOf(pointers);
    }
  }

  /**
   * Changes the file at {@code place}. The action gets the copy of the file and the range that the place has at the time of the write.
   * The engine follows the place with a range marker in the copy from before the first write. It runs the action among the updates, in
   * the order of the edits. So an earlier edit can make the text before the place longer, or replace the element that holds the place.
   * If another edit replaces the text of the whole place, the action is skipped. It is skipped inside a deleted file.
   *
   * @param place  the range in a physical file
   * @param action gets the writable copy of the file and the range of the place in it
   */
  record UpdateRange(@NotNull SmartPsiFileRange place,
                     @NotNull BiConsumer<? super PsiFile, ? super TextRange> action) implements ModPlanEdit {
  }

  /**
   * Replaces the text at {@code place} with {@code newText}, after every PSI edit of the plan, as the legacy rename writes the non-code
   * usages in the document. Of several replacements at one start offset of a file, the first one is written. It is skipped inside a
   * deleted file.
   *
   * @param place   the range in a physical file
   * @param newText the text that replaces the range
   */
  record ReplaceText(@NotNull SmartPsiFileRange place, @NotNull String newText) implements ModPlanEdit {
  }

  /**
   * Deletes a file or a directory. An edit inside it is skipped.
   */
  record Delete(@NotNull VirtualFile file) implements ModPlanEdit {
  }

  /**
   * Creates a text file.
   *
   * @param parent the directory of the new file
   * @param name   the name of the new file
   * @param text   the content of the new file
   */
  record Create(@NotNull VirtualFile parent, @NotNull @NonNls String name, @NotNull String text) implements ModPlanEdit {
  }

  /**
   * Creates a directory.
   *
   * @param directory the new directory, whose parent exists or is created by an earlier edit
   */
  record CreateDirectory(@NotNull FutureVirtualFile directory) implements ModPlanEdit {
    public CreateDirectory {
      if (!directory.isDirectory()) {
        throw new IllegalArgumentException("Not a directory: " + directory);
      }
    }
  }

  /**
   * Moves and/or renames a file or a directory.
   *
   * @param file            the file or directory to move
   * @param targetDirectory the directory it moves to
   * @param newName         the name it gets
   */
  record Move(@NotNull VirtualFile file,
              @NotNull VirtualFile targetDirectory,
              @NotNull @NonNls String newName) implements ModPlanEdit {
  }

  /**
   * @return an update of one element: {@code action} runs on the writable copy of {@code element}
   */
  @SuppressWarnings("unchecked")
  static <E extends PsiElement> @NotNull Update update(@NotNull E element, @NotNull Consumer<? super E> action) {
    return update(List.of(element), copies -> action.accept((E)copies.getFirst()));
  }

  /**
   * @return an update of the elements: {@code action} runs on their writable copies, in the same order
   */
  static @NotNull Update update(@NotNull List<? extends PsiElement> elements, @NotNull Consumer<? super List<PsiElement>> action) {
    return new Update(ContainerUtil.map(elements, element -> SmartPointerManager.createPointer(element)), action);
  }

  /**
   * A light element is deleted through its source, as its own {@code delete} does.
   *
   * @return a {@link Delete} of a file or a directory; an {@link Update} that deletes another element
   */
  static @NotNull ModPlanEdit delete(@NotNull PsiElement element) {
    PsiElement navigationElement = element.isPhysical() ? element : element.getNavigationElement();
    PsiElement source = navigationElement != element && navigationElement.isPhysical() ? navigationElement : element;
    VirtualFile file = source instanceof PsiFileSystemItem fileSystemItem ? fileSystemItem.getVirtualFile() : null;
    return file != null ? new Delete(file) : update(source, PsiElement::delete);
  }
}
