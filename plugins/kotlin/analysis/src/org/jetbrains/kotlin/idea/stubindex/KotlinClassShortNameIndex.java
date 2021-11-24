// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtClassOrObject;

import java.util.Collection;

public class KotlinClassShortNameIndex extends AbstractStringStubIndexExtension<KtClassOrObject> {
    private static final StubIndexKey<String, KtClassOrObject> KEY = KotlinIndexUtil.createIndexKey(KotlinClassShortNameIndex.class);

    private static final KotlinClassShortNameIndex ourInstance = new KotlinClassShortNameIndex();

    @NotNull
    public static KotlinClassShortNameIndex getInstance() {
        return ourInstance;
    }

    private KotlinClassShortNameIndex() {
        super(KtClassOrObject.class);
    }

    @NotNull
    @Override
    public StubIndexKey<String, KtClassOrObject> getKey() {
        return KEY;
    }

    @NotNull
    @Override
    public Collection<KtClassOrObject> get(@NotNull String s, @NotNull Project project, @NotNull GlobalSearchScope scope) {
        return StubIndex.getElements(KEY, s, project, scope, KtClassOrObject.class);
    }
}
