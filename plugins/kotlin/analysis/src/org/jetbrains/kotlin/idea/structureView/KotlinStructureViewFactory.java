// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structureView;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinIconProvider;
import org.jetbrains.kotlin.psi.KtFile;

public class KotlinStructureViewFactory implements PsiStructureViewFactory {
    @Override
    public StructureViewBuilder getStructureViewBuilder(@NotNull PsiFile psiFile) {
        if (!(psiFile instanceof KtFile)) {
            return null;
        }

        KtFile file = (KtFile) psiFile;
        return new TreeBasedStructureViewBuilder() {
            @NotNull
            @Override
            public StructureViewModel createStructureViewModel(@Nullable Editor editor) {
                return new KotlinStructureViewModel(file, editor);
            }

            @Override
            public boolean isRootNodeShown() {
                return !KotlinIconProvider.Companion.isSingleClassFile(file);
            }
        };
    }
}
