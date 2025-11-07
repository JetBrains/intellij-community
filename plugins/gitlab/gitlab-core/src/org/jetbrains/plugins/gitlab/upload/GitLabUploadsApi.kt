// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.upload

import com.intellij.collaboration.api.json.loadJsonValue
import com.intellij.collaboration.util.resolveRelative
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import org.jetbrains.plugins.gitlab.api.dto.GitLabUploadRestDTO
import org.jetbrains.plugins.gitlab.api.restApiUri
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.*


@SinceGitLab("15.10")
suspend fun GitLabApi.Rest.markdownUploadFile(
  project: GitLabProjectCoordinates,
  path: Path,
): HttpResponse<out GitLabUploadRestDTO> {
  val uri = project.restApiUri.resolveRelative("uploads")
  val filename = path.fileName.toString()

  val mimeType = Files.probeContentType(path) ?: "application/octet-stream"

  val boundary = "FormBoundary" + UUID.randomUUID()
  val boundaryStart = "--$boundary\r\n" +
                      "Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\n" +
                      "Content-Type: $mimeType\r\n\r\n"
  val boundaryEnd = "\r\n--$boundary--\r\n"

  val bodyPublisher = BodyPublishers.concat(
    BodyPublishers.ofString(boundaryStart),
    BodyPublishers.ofInputStream { Files.newInputStream(path) },
    BodyPublishers.ofString(boundaryEnd)
  )

  val httpRequest = request(uri)
    .POST(bodyPublisher)
    .header("Content-Type", "multipart/form-data; boundary=$boundary")
    .build()

  return loadJsonValue(httpRequest)
}