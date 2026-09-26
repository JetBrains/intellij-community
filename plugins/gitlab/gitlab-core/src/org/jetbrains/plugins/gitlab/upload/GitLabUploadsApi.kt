// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.upload

import com.intellij.collaboration.api.httpclient.HttpClientUtil
import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.util.resolveRelative
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import org.jetbrains.plugins.gitlab.api.dto.GitLabUploadRestDTO
import org.jetbrains.plugins.gitlab.api.projectApiUrl
import java.io.InputStream
import java.net.http.HttpRequest.BodyPublishers
import java.util.UUID


@SinceGitLab("15.10")
internal suspend fun GitLabApi.Rest.markdownUploadFile(
    projectId: String,
    filename: String,
    mimeType: String,
    fileInputStream: InputStream,
): GitLabUploadRestDTO {
  val boundary = "FormBoundary" + UUID.randomUUID()
  val boundaryStart = "--$boundary\r\n" +
                      "Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\n" +
                      "Content-Type: $mimeType\r\n\r\n"
  val boundaryEnd = "\r\n--$boundary--\r\n"

  val bodyPublisher = BodyPublishers.concat(
    BodyPublishers.ofString(boundaryStart),
    BodyPublishers.ofInputStream { fileInputStream },
    BodyPublishers.ofString(boundaryEnd)
  )

  val uri = projectApiUrl(projectId).resolveRelative("uploads")
  return request(uri)
    .POST(bodyPublisher)
    .header(HttpClientUtil.CONTENT_TYPE_HEADER, "multipart/form-data; boundary=$boundary")
    .build()
    .loadJsonValue<GitLabUploadRestDTO>()
    .body()
}