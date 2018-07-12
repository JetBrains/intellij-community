// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.util.ThrowableConvertor
import org.jetbrains.plugins.github.api.GithubApiRequest.*
import org.jetbrains.plugins.github.api.data.*
import org.jetbrains.plugins.github.api.requests.GithubAuthorizationCreateRequest
import org.jetbrains.plugins.github.api.requests.GithubGistRequest
import org.jetbrains.plugins.github.api.requests.GithubRepoRequest
import org.jetbrains.plugins.github.api.requests.GithubRequestPagination
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import java.awt.Image

/**
 * Collection of factory methods for API requests used in plugin
 * TODO: improve url building (DSL?)
 */
object GithubApiRequests {
  object CurrentUser : Entity("/user") {
    @JvmStatic
    fun get(server: GithubServerPath) = get(getUrl(server, urlSuffix))

    @JvmStatic
    fun get(url: String) = Get.json<GithubAuthenticatedUser>(url).withOperationName("get profile information")

    @JvmStatic
    fun getAvatar(url: String) = object : Get<Image>(url) {
      override fun extractResult(response: GithubApiResponse): Image {
        return response.handleBody(ThrowableConvertor {
          GithubApiContentHelper.loadImage(it)
        })
      }
    }.withOperationName("get profile avatar")

    object Repos : Entity("/repos") {
      @JvmOverloads
      @JvmStatic
      fun pages(server: GithubServerPath, allAssociated: Boolean = true) = GithubApiPagesLoader.Request(get(server, allAssociated), ::get)

      @JvmOverloads
      @JvmStatic
      fun get(server: GithubServerPath, allAssociated: Boolean = true, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, CurrentUser.urlSuffix, urlSuffix,
                   getQuery(if (allAssociated) "" else "type=owner", pagination?.toString().orEmpty())))

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubRepo>(url).withOperationName("get user repositories")

      @JvmStatic
      fun create(server: GithubServerPath, name: String, description: String, private: Boolean) =
        Post.json<GithubRepo>(getUrl(server, CurrentUser.urlSuffix, urlSuffix),
                              GithubRepoRequest(name, description, private))
          .withOperationName("create user repository")
    }

    object RepoSubs : Entity("/subscriptions") {
      @JvmStatic
      fun pages(server: GithubServerPath) = GithubApiPagesLoader.Request(get(server), ::get)

      @JvmOverloads
      @JvmStatic
      fun get(server: GithubServerPath, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, CurrentUser.urlSuffix, urlSuffix, getQuery(pagination?.toString().orEmpty())))

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubRepo>(url).withOperationName("get repository subscriptions")
    }
  }

  object Repos : Entity("/repos") {
    @JvmStatic
    fun get(server: GithubServerPath, username: String, repoName: String) =
      Get.Optional.json<GithubRepoDetailed>(getUrl(server, urlSuffix, "/$username/$repoName"))
        .withOperationName("get information for repository $username/$repoName")

    @JvmStatic
    fun delete(server: GithubServerPath, username: String, repoName: String) =
      Delete(getUrl(server, urlSuffix, "/$username/$repoName")).withOperationName("delete repository $username/$repoName")
  }

  object Gists : Entity("/gists") {
    @JvmStatic
    fun create(server: GithubServerPath,
               contents: List<GithubGistRequest.FileContent>, description: String, public: Boolean) =
      Post.json<GithubGist>(getUrl(server, urlSuffix), GithubGistRequest(contents, description, public))
        .withOperationName("create gist")

    @JvmStatic
    fun get(server: GithubServerPath, id: String) = Get.Optional.json<GithubGist>(getUrl(server, urlSuffix, "/$id"))
      .withOperationName("get gist $id")

    @JvmStatic
    fun delete(server: GithubServerPath, id: String) = Delete(getUrl(server, urlSuffix, "/$id"))
      .withOperationName("delete gist $id")
  }

  object Auth : Entity("/authorizations") {
    @JvmStatic
    fun create(server: GithubServerPath, scopes: List<String>, note: String) =
      Post.json<GithubAuthorization>(getUrl(server, urlSuffix), GithubAuthorizationCreateRequest(scopes, note, null))
        .withOperationName("create authorization $note")

    @JvmStatic
    fun get(server: GithubServerPath) = Get.jsonList<GithubAuthorization>(getUrl(server, urlSuffix))
      .withOperationName("get authorizations")
  }

  abstract class Entity(val urlSuffix: String)

  private fun getUrl(server: GithubServerPath, suffix: String) = server.toApiUrl() + suffix

  private fun getUrl(server: GithubServerPath, vararg suffixes: String) = StringBuilder(server.toApiUrl()).append(*suffixes).toString()

  private fun getQuery(vararg queryParts: String): String {
    val builder = StringBuilder()
    for (part in queryParts) {
      if (part.isEmpty()) continue
      if (builder.isEmpty()) builder.append("?")
      else builder.append("&")
      builder.append(part)
    }
    return builder.toString()
  }
}