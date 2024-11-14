// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import org.jetbrains.kotlin.idea.base.scripting.KotlinBaseScriptingBundle
import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.dependencies.ArtifactWithLocation
import kotlin.script.experimental.dependencies.ExternalDependenciesResolver
import kotlin.script.experimental.dependencies.RepositoryCoordinates

class ReportingExternalDependenciesResolver(
  private val delegate: ExternalDependenciesResolver,
  private val dependenciesResolutionService: DependencyResolutionService,
) : ExternalDependenciesResolver {

    override fun acceptsArtifact(artifactCoordinates: String): Boolean =
        delegate.acceptsArtifact(artifactCoordinates)

    override fun acceptsRepository(repositoryCoordinates: RepositoryCoordinates): Boolean =
        delegate.acceptsRepository(repositoryCoordinates)

    override fun addRepository(
        repositoryCoordinates: RepositoryCoordinates,
        options: ExternalDependenciesResolver.Options,
        sourceCodeLocation: SourceCode.LocationWithId?
    ): ResultWithDiagnostics<Boolean> =
        delegate.addRepository(repositoryCoordinates, options, sourceCodeLocation)

    override suspend fun resolve(
        artifactCoordinates: String,
        options: ExternalDependenciesResolver.Options,
        sourceCodeLocation: SourceCode.LocationWithId?
    ): ResultWithDiagnostics<List<File>> {
        dependenciesResolutionService.reporter?.indeterminateStep(KotlinBaseScriptingBundle.message("progress.text.resolving", artifactCoordinates))
        return super.resolve(artifactCoordinates, options, sourceCodeLocation)
    }

    override suspend fun resolve(
        artifactsWithLocations: List<ArtifactWithLocation>,
        options: ExternalDependenciesResolver.Options
    ): ResultWithDiagnostics<List<File>> {
        return super.resolve(artifactsWithLocations, options)
    }
}