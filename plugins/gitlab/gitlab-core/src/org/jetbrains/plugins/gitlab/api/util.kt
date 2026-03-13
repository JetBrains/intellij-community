// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.collaboration.util.resolveRelative
import java.net.URI
import java.net.URLEncoder

fun GitLabApi.projectApiUrl(projectId: String): URI = server.projectApiUri(URLEncoder.encode(projectId, Charsets.UTF_8))

fun GitLabServerPath.projectApiUri(projectId: String): URI = restApiUri
  .resolveRelative("projects/")
  .resolveRelative("$projectId/")
