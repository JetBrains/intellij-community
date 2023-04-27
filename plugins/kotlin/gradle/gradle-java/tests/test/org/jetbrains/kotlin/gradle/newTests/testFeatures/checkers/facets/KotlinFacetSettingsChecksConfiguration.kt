// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.facets

class KotlinFacetSettingsChecksConfiguration {
    var excludedFacetFields: Set<FacetField>? = null
    var includedFacetFields: Set<FacetField>? = null
}
