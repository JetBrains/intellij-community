// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtTypeAlias;

import java.util.Collection;

public class KotlinTypeAliasShortNameIndex extends AbstractStringStubIndexExtension<KtTypeAlias> {
    private static final StubIndexKey<String, KtTypeAlias> KEY = KotlinIndexUtil.createIndexKey(KotlinTypeAliasShortNameIndex.class);

    private static final KotlinTypeAliasShortNameIndex ourInstance = new KotlinTypeAliasShortNameIndex();

    public static KotlinTypeAliasShortNameIndex getInstance() {
        return ourInstance;
    }

    private KotlinTypeAliasShortNameIndex() {
        super(KtTypeAlias.class);
    }

    @NotNull
    @Override
    public StubIndexKey<String, KtTypeAlias> getKey() {
        return KEY;
    }

    @NotNull
    @Override
    public Collection<KtTypeAlias> get(@NotNull String s, @NotNull Project project, @NotNull GlobalSearchScope scope) {
        return StubIndex.getElements(KEY, s, project, scope, KtTypeAlias.class);
    }
}
