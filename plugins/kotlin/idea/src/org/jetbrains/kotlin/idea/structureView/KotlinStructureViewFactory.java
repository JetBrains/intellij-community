// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.structureView;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.treeView.smartTree.NodeProvider;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.base.util.KotlinSingleClassFileAnalyzer;
import org.jetbrains.kotlin.psi.KtFile;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class KotlinStructureViewFactory implements PsiStructureViewFactory {
    private static final List<NodeProvider<?>> NODE_PROVIDERS = Collections.singletonList(new KotlinInheritedMembersNodeProvider());

    @Override
    public StructureViewBuilder getStructureViewBuilder(@NotNull PsiFile psiFile) {
        if (!(psiFile instanceof KtFile file)) {
            return null;
        }

        boolean isSingleClassFile = KotlinSingleClassFileAnalyzer.isSingleClassFile(file);

        return new TreeBasedStructureViewBuilder() {
            @Override
            public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
                return new KotlinStructureViewModel(file, editor, new KotlinStructureViewElement(file, false)) {
                    @Override
                    public @NotNull Collection<NodeProvider<?>> getNodeProviders() {
                        return NODE_PROVIDERS;
                    }
                };
            }

            @Override
            public boolean isRootNodeShown() {
                return !isSingleClassFile;
            }
        };
    }
}
