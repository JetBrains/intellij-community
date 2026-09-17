// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.dependencies

import com.intellij.jarRepository.JarRepositoryAuthenticationDataProvider
import com.intellij.jarRepository.JarRepositoryAuthenticationDataProvider.AuthenticationData
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.application
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.APP)
internal class ScriptRepositoryCredentials {

    private val byRepositoryId = ConcurrentHashMap<String, AuthenticationData>()

    fun remember(repositories: List<ScriptArtifactRepository>) {
        for ((id, _, username, password) in repositories) {
            username ?: continue
            password ?: continue
            byRepositoryId[id] = AuthenticationData(username, password)
        }
    }

    fun forRepository(id: String): AuthenticationData? = byRepositoryId[id]
}

internal class ScriptRepositoryAuthenticationDataProvider : JarRepositoryAuthenticationDataProvider {
    override fun provideAuthenticationData(remote: RemoteRepositoryDescription): AuthenticationData? =
        application.service<ScriptRepositoryCredentials>().forRepository(remote.id)
}
