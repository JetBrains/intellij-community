// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.low.level.api.ide

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.services.PackagePartProviderFactory
import org.jetbrains.kotlin.idea.caches.resolve.IDEPackagePartProvider
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider

internal class PackagePartProviderFactoryIdeImpl : PackagePartProviderFactory() {
    override fun createPackagePartProviderForLibrary(scope: GlobalSearchScope): PackagePartProvider {
        return IDEPackagePartProvider(scope)
    }
}