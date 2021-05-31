// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtNamedFunction;

import java.util.Collection;

/**
 * Stores package top level function (both extension and non-extension) full qualified names.
 */
public class KotlinTopLevelFunctionFqnNameIndex extends StringStubIndexExtension<KtNamedFunction> {
    private static final StubIndexKey<String, KtNamedFunction> KEY = KotlinIndexUtil.createIndexKey(KotlinTopLevelFunctionFqnNameIndex.class);

    private static final KotlinTopLevelFunctionFqnNameIndex INSTANCE = new KotlinTopLevelFunctionFqnNameIndex();

    @NotNull
    public static KotlinTopLevelFunctionFqnNameIndex getInstance() {
        return INSTANCE;
    }

    private KotlinTopLevelFunctionFqnNameIndex() {}

    @NotNull
    @Override
    public StubIndexKey<String, KtNamedFunction> getKey() {
        return KEY;
    }

    @NotNull
    @Override
    public Collection<KtNamedFunction> get(@NotNull String s, @NotNull Project project, @NotNull GlobalSearchScope scope) {
        return StubIndex.getElements(KEY, s, project, scope, KtNamedFunction.class);
    }

    // temporary hack, see comments in findCandidateDeclarationsInIndex (findDecompiledDeclaration.kt)
    @NotNull
    public Collection<KtNamedFunction> getNoScopeWrap(@NotNull String s, @NotNull Project project, @NotNull GlobalSearchScope scope) {
        return StubIndex.getElements(KEY, s, project, scope, KtNamedFunction.class);
    }
}
