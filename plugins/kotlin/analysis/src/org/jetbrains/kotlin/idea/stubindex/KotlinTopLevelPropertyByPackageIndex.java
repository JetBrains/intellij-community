// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtProperty;

import java.util.Collection;

public class KotlinTopLevelPropertyByPackageIndex extends AbstractStringStubIndexExtension<KtProperty> {
    private static final StubIndexKey<String, KtProperty> KEY = KotlinIndexUtil.createIndexKey(KotlinTopLevelPropertyByPackageIndex.class);

    private static final KotlinTopLevelPropertyByPackageIndex ourInstance = new KotlinTopLevelPropertyByPackageIndex();

    public static KotlinTopLevelPropertyByPackageIndex getInstance() {
        return ourInstance;
    }

    private KotlinTopLevelPropertyByPackageIndex() {
        super(KtProperty.class);
    }

    @NotNull
    @Override
    public StubIndexKey<String, KtProperty> getKey() {
        return KEY;
    }

    @NotNull
    @Override
    public Collection<KtProperty> get(@NotNull String fqName, @NotNull Project project, @NotNull GlobalSearchScope scope) {
        return StubIndex.getElements(KEY, fqName, project, scope, KtProperty.class);
    }
}
