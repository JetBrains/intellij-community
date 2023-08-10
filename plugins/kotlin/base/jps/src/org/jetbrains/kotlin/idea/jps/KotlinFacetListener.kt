// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jps

import com.intellij.compiler.server.BuildManager
import com.intellij.facet.ProjectFacetListener
import org.jetbrains.kotlin.idea.facet.KotlinFacet

class KotlinFacetListener: ProjectFacetListener<KotlinFacet> {
    override fun facetConfigurationChanged(facet: KotlinFacet) {
        BuildManager.getInstance().clearState(facet.module.project)
    }
}
