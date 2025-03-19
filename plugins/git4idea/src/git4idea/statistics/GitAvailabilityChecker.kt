// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.statistics

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.net.IdeHttpClientHelpers
import git4idea.repo.GitRepository
import org.apache.http.HttpStatus
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpHead
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.jetbrains.annotations.TestOnly

@Service(Service.Level.PROJECT)
class GitAvailabilityChecker(val project: Project) {
  fun checkRepoStatus(gitRepository: GitRepository): List<RepositoryAvailability> {
    if (gitRepository.remotes.isEmpty()) return listOf(RepositoryAvailability.LOCAL)
    return gitRepository.remotes.take(10).map { checkRemoteStatus(it.firstUrl) }
  }

  @TestOnly
  fun getHttpUrlFromRemote(remote: String): String? {
    return parseGitRemoteUrl(remote)
  }


  private fun checkRemoteStatus(remote: String?): RepositoryAvailability {
    if (remote == null) return RepositoryAvailability.LOCAL
    val httpsUrl = parseGitRemoteUrl(remote) ?: return RepositoryAvailability.UNKNOWN_HOST
    val result = sendHeadRequest(httpsUrl)
    return if (result) RepositoryAvailability.PUBLIC else RepositoryAvailability.PRIVATE
  }

  private fun sendHeadRequest(url: String): Boolean {
    val httpClient = createHttpClient(url)
    httpClient.use {
      try {
        val response = it.execute(HttpHead(url))
        return response.statusLine.statusCode == HttpStatus.SC_OK
      }
      catch (e: Exception) {
        LOG.warn(e)
        return false
      }
    }
  }

  private fun createHttpClient(url: String): CloseableHttpClient {
    val requestConfigBuilder = getRequestConfigBuilder(url)
    val builder = HttpClientBuilder.create()
      .setDefaultRequestConfig(requestConfigBuilder.build())
      .build()
    return builder
  }

  private fun getRequestConfigBuilder(url: String): RequestConfig.Builder {
    val requestConfigBuilder = RequestConfig.custom()
      .setConnectTimeout(CONNECTION_TIMEOUT)
      .setSocketTimeout(CONNECTION_TIMEOUT)
      .setCookieSpec(CookieSpecs.STANDARD)
    IdeHttpClientHelpers.ApacheHttpClient4.setProxyForUrlIfEnabled(requestConfigBuilder, url)
    return requestConfigBuilder
  }

  private fun parseGitRemoteUrl(url: String): String? {
    val match = REGEXP.matchEntire(url)
    if (match == null) return null

    val protocol = match.groups[1]?.value ?: "https://"
    val host = match.groups[2]?.value ?: match.groups[4]?.value ?: match.groups[5]?.value ?: return null
    val path = match.groups[6]?.value ?: return null
    return "$protocol$host/$path"
  }

  companion object {
    private val REGEXP = Regex("^(?:(https?://)(?:.*?@)?(.*?)/|(ssh://)(?:.*@)?(.*?)(?::.*?)?(?:/|(?=~))|.*?@(.*?):)/?(.*)$")
    private const val CONNECTION_TIMEOUT = 3000
    private val LOG = Logger.getInstance(GitAvailabilityChecker::class.java)

    fun getInstance(project: Project) = project.service<GitAvailabilityChecker>()
  }
}


enum class RepositoryAvailability {
  PUBLIC, PRIVATE, LOCAL, UNKNOWN_HOST
}