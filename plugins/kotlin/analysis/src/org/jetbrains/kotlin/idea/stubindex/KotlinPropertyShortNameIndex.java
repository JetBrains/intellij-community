// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtNamedDeclaration;
import java.util.Collection;

public class KotlinPropertyShortNameIndex extends AbstractStringStubIndexExtension<KtNamedDeclaration> {
    private static final StubIndexKey<String, KtNamedDeclaration> KEY = KotlinIndexUtil.createIndexKey(KotlinPropertyShortNameIndex.class);

    private static final KotlinPropertyShortNameIndex ourInstance = new KotlinPropertyShortNameIndex();

    public static KotlinPropertyShortNameIndex getInstance() {
        return ourInstance;
    }

    private KotlinPropertyShortNameIndex() {
        super(KtNamedDeclaration.class);
    }

    @NotNull
    @Override
    public StubIndexKey<String, KtNamedDeclaration> getKey() {
        return KEY;
    }

    @NotNull
    @Override
    public Collection<KtNamedDeclaration> get(@NotNull String s, @NotNull Project project, @NotNull GlobalSearchScope scope) {
        return StubIndex.getElements(KEY, s, project, scope, KtNamedDeclaration.class);
    }

}
