// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtFile;

import java.util.Collection;

public class KotlinExactPackagesIndex extends StringStubIndexExtension<KtFile> {
    private static final StubIndexKey<String, KtFile> KEY = KotlinIndexUtil.createIndexKey(KotlinExactPackagesIndex.class);

    private static final KotlinExactPackagesIndex ourInstance = new KotlinExactPackagesIndex();

    @NotNull
    public static KotlinExactPackagesIndex getInstance() {
        return ourInstance;
    }

    private KotlinExactPackagesIndex() {}

    @NotNull
    @Override
    public StubIndexKey<String, KtFile> getKey() {
        return KEY;
    }

    @NotNull
    @Override
    public Collection<KtFile> get(@NotNull String fqName, @NotNull Project project, @NotNull GlobalSearchScope scope) {
        return StubIndex.getElements(KEY, fqName, project, scope, KtFile.class);
    }
}
