// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.singleton

/**
 * Represents the marker for transformation that is guaranteed to add data to
 * [org.jetbrains.plugins.groovy.transformations.TransformationContext] without implicitly calculating transformations
 * for other [org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition]. Prefer using this variant whenever possible.
 */
interface LightAstTransformationSupport
