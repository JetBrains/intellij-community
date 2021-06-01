// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtProperty;

import java.util.Collection;

public class KotlinProbablyNothingPropertyShortNameIndex extends StringStubIndexExtension<KtProperty> {
    private static final StubIndexKey<String, KtProperty> KEY = KotlinIndexUtil.createIndexKey(KotlinProbablyNothingPropertyShortNameIndex.class);

    private static final KotlinProbablyNothingPropertyShortNameIndex ourInstance = new KotlinProbablyNothingPropertyShortNameIndex();

    public static KotlinProbablyNothingPropertyShortNameIndex getInstance() {
        return ourInstance;
    }

    private KotlinProbablyNothingPropertyShortNameIndex() {}

    @NotNull
    @Override
    public StubIndexKey<String, KtProperty> getKey() {
        return KEY;
    }

    @NotNull
    @Override
    public Collection<KtProperty> get(@NotNull String s, @NotNull Project project, @NotNull GlobalSearchScope scope) {
        return StubIndex.getElements(KEY, s, project, scope, KtProperty.class);
    }
}
