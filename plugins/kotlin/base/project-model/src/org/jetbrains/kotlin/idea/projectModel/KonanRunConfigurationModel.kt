// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel

import java.io.Serializable

interface KonanRunConfigurationModel : Serializable {
    val workingDirectory: String
    val programParameters: List<String>
    val environmentVariables: Map<String, String>

    fun isNotEmpty() = workingDirectory.isNotEmpty() || programParameters.isNotEmpty() || environmentVariables.isNotEmpty()

    companion object {
        const val NO_WORKING_DIRECTORY = ""
        val NO_PROGRAM_PARAMETERS = emptyList<String>()
        val NO_ENVIRONMENT_VARIABLES = emptyMap<String, String>()
    }
}
