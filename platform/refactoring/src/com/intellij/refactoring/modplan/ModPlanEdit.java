// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.function.Consumer;

/**
 * An edit of a ModPlan refactoring. A language describes it during the analysis; the engine applies it to non-physical copies,
 * so nothing can ask the user or show UI.
 * <p>
 * An update keeps the elements it changes as smart pointers, so it outlives a read action. Build it with {@link #update}, {@link #delete}
 * or with a pointer to the element. The engine turns an edit with a gone element into no command, which refuses the refactoring.
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
