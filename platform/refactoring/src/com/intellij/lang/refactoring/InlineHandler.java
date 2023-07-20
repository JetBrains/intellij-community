// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.refactoring;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

/**
 * Interface that should be implemented by the language in order to provide inline functionality and possibly
 * participate in inline of elements in other languages this language may reference.
 *
 * @see InlineHandlers#getInlineHandlers(com.intellij.lang.Language)
 */
public interface InlineHandler {
  interface Settings {
    /**
     * @return {@code true} if as a result of refactoring setup only the reference where refactoring
     * was triggered should be inlined.
     */
    boolean isOnlyOneReferenceToInline();

    /**
     * Special settings for the case when inline cannot be performed due to already reported (by error hint) problem
     */
    Settings CANNOT_INLINE_SETTINGS = new Settings() {
      @Override
      public boolean isOnlyOneReferenceToInline() {
        return false;
      }
    };
  }

  default boolean canInlineElement(@NotNull PsiElement element) {
    return true;
  }

  /**
   * @param element            element to be inlined
   * @param invokedOnReference {@code true} if the user invoked the refactoring on an element reference
   * @param editor             in case refactoring has been called in the editor
   * @return {@code Settings} object in case refactoring should be performed or {@code null} otherwise
   */
  @Nullable
  Settings prepareInlineElement(@NotNull PsiElement element, @Nullable Editor editor, boolean invokedOnReference);

  /**
   * @param element inlined element
   */
  void removeDefinition(@NotNull PsiElement element, @NotNull Settings settings);

  /**
   * @param element  inlined element
   * @param settings inlining settings
   * @return Inliner instance to be used for inlining references in this language
   */
  @Nullable
  Inliner createInliner(@NotNull PsiElement element, @NotNull Settings settings);

  interface Inliner {
    /**
     * @param reference  reference to inlined element
     * @param referenced inlined element
     * @return set of conflicts inline of this element to the place denoted by reference would incur
     * or {@code null} if no conflicts detected.
     */
    @Nullable
    MultiMap<PsiElement, @DialogMessage String> getConflicts(@NotNull PsiReference reference, @NotNull PsiElement referenced);

    /**
     * Perform actual inline of element to the point where it is referenced.
     *
     * @param usage      usage of inlined element
     * @param referenced inlined element
     */
    void inlineUsage(@NotNull UsageInfo usage, @NotNull PsiElement referenced);
  }
}
