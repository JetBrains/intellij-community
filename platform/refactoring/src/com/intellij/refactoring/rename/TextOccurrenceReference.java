// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.ApiStatus;

/**
 * A reference that a text match creates, not a language construct.
 * <p>
 * A plain name in a Markdown code span is an example. Such a reference resolves through a name match,
 * so it can point at a declaration that the author did not mean.
 * <p>
 * The rename refactoring changes such a reference only when the user keeps the text occurrence option.
 * The user controls the edit with the same option that controls a plain text occurrence.
 *
 * @see RenameUtil#findUsages
 */
@ApiStatus.Experimental
public interface TextOccurrenceReference extends PsiReference {
}
