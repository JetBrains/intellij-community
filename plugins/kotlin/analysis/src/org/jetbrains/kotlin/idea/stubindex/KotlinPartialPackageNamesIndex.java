// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.psi.KtFile;

import java.util.Collection;

public class KotlinPartialPackageNamesIndex extends StringStubIndexExtension<KtFile> {
    private static final StubIndexKey<String, KtFile> KEY = KotlinIndexUtil.createIndexKey(KotlinPartialPackageNamesIndex.class);

    private static final KotlinPartialPackageNamesIndex ourInstance = new KotlinPartialPackageNamesIndex();

    @NotNull
    public static KotlinPartialPackageNamesIndex getInstance() {
        return ourInstance;
    }

    private KotlinPartialPackageNamesIndex() {}

    @NotNull
    @Override
    public StubIndexKey<String, KtFile> getKey() {
        return KEY;
    }

    /**
     * Picks partial name from <code>fqName</code>,
     * actually it is the most top segment from <code>fqName</code>
     *
     * @param fqName
     * @return
     */
    public static @NotNull FqName toPartialFqName(@NotNull FqName fqName) {
        // so far we use only the most top segment frm fqName if it is not a root
        // i.e. only `foo` from `foo.bar.zoo`

        String asString = fqName.asString();
        int dotIndex = asString.indexOf('.');
        return dotIndex > 0 ? new FqName(asString.substring(0, dotIndex)) : fqName;
    }

    @NotNull
    @Override
    public Collection<KtFile> get(@NotNull String fqName, @NotNull Project project, @NotNull GlobalSearchScope scope) {
        return StubIndex.getElements(KEY, fqName, project, scope, KtFile.class);
    }
}
