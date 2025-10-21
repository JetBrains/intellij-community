// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.k2.configurations.MainKtsScriptConfigurationProvider
import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.dependencies.ExternalDependenciesResolver
import kotlin.script.experimental.dependencies.RepositoryCoordinates

internal class ReportingExternalDependenciesResolver(
    private val delegate: ExternalDependenciesResolver,
    private val configurationProvider: MainKtsScriptConfigurationProvider,
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
        return configurationProvider.reporter?.indeterminateStep(
            KotlinBaseScriptingBundle.message(
                "progress.text.resolving",
                artifactCoordinates
            )
        ) {
            delegate.resolve(artifactCoordinates, options, sourceCodeLocation)
        } ?: delegate.resolve(artifactCoordinates, options, sourceCodeLocation)

    }
}