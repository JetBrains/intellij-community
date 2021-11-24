// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtAnnotationEntry;

import java.util.Collection;

public class KotlinAnnotationsIndex extends AbstractStringStubIndexExtension<KtAnnotationEntry> {
    private static final StubIndexKey<String, KtAnnotationEntry> KEY = KotlinIndexUtil.createIndexKey(KotlinAnnotationsIndex.class);

    private static final KotlinAnnotationsIndex ourInstance = new KotlinAnnotationsIndex();

    public static KotlinAnnotationsIndex getInstance() {
        return ourInstance;
    }

    private KotlinAnnotationsIndex() {
        super(KtAnnotationEntry.class);
    }

    @NotNull
    @Override
    public StubIndexKey<String, KtAnnotationEntry> getKey() {
        return KEY;
    }

    @NotNull
    @Override
    public Collection<KtAnnotationEntry> get(@NotNull String s, @NotNull Project project, @NotNull GlobalSearchScope scope) {
        return StubIndex.getElements(KEY, s, project, scope, KtAnnotationEntry.class);
    }

    @Override
    public int getVersion() {
        return super.getVersion() + 1;
    }
}
