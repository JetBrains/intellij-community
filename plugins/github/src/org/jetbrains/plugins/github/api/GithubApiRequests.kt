// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.openapi.util.io.StreamUtil
import com.intellij.util.ThrowableConvertor
import org.jetbrains.plugins.github.api.GithubApiRequest.*
import org.jetbrains.plugins.github.api.data.*
import org.jetbrains.plugins.github.api.requests.*
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.api.util.GithubApiSearchQueryBuilder
import org.jetbrains.plugins.github.api.util.GithubApiUrlQueryBuilder
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
      fun pages(server: GithubServerPath, allAssociated: Boolean = true, pagination: GithubRequestPagination? = null) =
        GithubApiPagesLoader.Request(get(server, allAssociated, pagination), ::get)

      @JvmOverloads
      @JvmStatic
      fun get(server: GithubServerPath, allAssociated: Boolean = true, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, CurrentUser.urlSuffix, urlSuffix,
                   getQuery(if (allAssociated) "" else "type=owner", pagination?.toString().orEmpty())))

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubRepo>(url).withOperationName("get user repositories")

      @JvmStatic
      fun create(server: GithubServerPath, name: String, description: String, private: Boolean, autoInit: Boolean? = null) =
        Post.json<GithubRepo>(getUrl(server, CurrentUser.urlSuffix, urlSuffix),
                              GithubRepoRequest(name, description, private, autoInit))
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

  object Organisations : Entity("/orgs") {

    object Repos : Entity("/repos") {
      @JvmStatic
      fun pages(server: GithubServerPath, organisation: String) = GithubApiPagesLoader.Request(get(server, organisation), ::get)

      @JvmOverloads
      @JvmStatic
      fun get(server: GithubServerPath, organisation: String, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, Organisations.urlSuffix, "/", organisation, urlSuffix, pagination?.toString().orEmpty()))

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubRepo>(url).withOperationName("get organisation repositories")

      @JvmStatic
      fun create(server: GithubServerPath, organisation: String, name: String, description: String, private: Boolean) =
        Post.json<GithubRepo>(getUrl(server, Organisations.urlSuffix, "/", organisation, urlSuffix),
                              GithubRepoRequest(name, description, private, null))
          .withOperationName("create organisation repository")
    }
  }

  object Repos : Entity("/repos") {
    @JvmStatic
    fun get(server: GithubServerPath, username: String, repoName: String) =
      Get.Optional.json<GithubRepoDetailed>(getUrl(server, urlSuffix, "/$username/$repoName"))
        .withOperationName("get information for repository $username/$repoName")

    @JvmStatic
    fun delete(server: GithubServerPath, username: String, repoName: String) =
      delete(getUrl(server, urlSuffix, "/$username/$repoName")).withOperationName("delete repository $username/$repoName")

    @JvmStatic
    fun delete(url: String) = Delete.json<Unit>(url).withOperationName("delete repository at $url")

    object Branches : Entity("/branches") {
      @JvmStatic
      fun pages(server: GithubServerPath, username: String, repoName: String) =
        GithubApiPagesLoader.Request(get(server, username, repoName), ::get)

      @JvmOverloads
      @JvmStatic
      fun get(server: GithubServerPath, username: String, repoName: String, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, getQuery(pagination?.toString().orEmpty())))

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubBranch>(url).withOperationName("get branches")
    }

    object Forks : Entity("/forks") {

      @JvmStatic
      fun create(server: GithubServerPath, username: String, repoName: String) =
        Post.json<GithubRepo>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix), Any())
          .withOperationName("fork repository $username/$repoName for cuurent user")

      @JvmStatic
      fun pages(server: GithubServerPath, username: String, repoName: String) =
        GithubApiPagesLoader.Request(get(server, username, repoName), ::get)

      @JvmOverloads
      @JvmStatic
      fun get(server: GithubServerPath, username: String, repoName: String, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, getQuery(pagination?.toString().orEmpty())))

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubRepo>(url).withOperationName("get forks")
    }

    object Assignees : Entity("/assignees") {

      @JvmStatic
      fun pages(server: GithubServerPath, username: String, repoName: String) =
        GithubApiPagesLoader.Request(get(server, username, repoName), ::get)

      @JvmOverloads
      @JvmStatic
      fun get(server: GithubServerPath, username: String, repoName: String, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, getQuery(pagination?.toString().orEmpty())))

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubUser>(url).withOperationName("get assignees")
    }

    object Labels : Entity("/labels") {

      @JvmStatic
      fun pages(server: GithubServerPath, username: String, repoName: String) =
        GithubApiPagesLoader.Request(get(server, username, repoName), ::get)

      @JvmOverloads
      @JvmStatic
      fun get(server: GithubServerPath, username: String, repoName: String, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, getQuery(pagination?.toString().orEmpty())))

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubIssueLabel>(url).withOperationName("get assignees")
    }

    object Collaborators : Entity("/collaborators") {

      @JvmStatic
      fun pages(server: GithubServerPath, username: String, repoName: String) =
        GithubApiPagesLoader.Request(get(server, username, repoName), ::get)

      @JvmOverloads
      @JvmStatic
      fun get(server: GithubServerPath, username: String, repoName: String, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, getQuery(pagination?.toString().orEmpty())))

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubUserWithPermissions>(url).withOperationName("get collaborators")

      @JvmStatic
      fun add(server: GithubServerPath, username: String, repoName: String, collaborator: String) =
        Put.json<Unit>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, "/", collaborator))
    }

    object Issues : Entity("/issues") {

      @JvmStatic
      fun create(server: GithubServerPath,
                 username: String,
                 repoName: String,
                 title: String,
                 body: String? = null,
                 milestone: Long? = null,
                 labels: List<String>? = null,
                 assignees: List<String>? = null) =
        Post.json<GithubIssue>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix),
                               GithubCreateIssueRequest(title, body, milestone, labels, assignees))

      @JvmStatic
      fun pages(server: GithubServerPath, username: String, repoName: String,
                state: String? = null, assignee: String? = null) = GithubApiPagesLoader.Request(get(server, username, repoName,
                                                                                                    state, assignee), ::get)

      @JvmStatic
      fun get(server: GithubServerPath, username: String, repoName: String,
              state: String? = null, assignee: String? = null, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix,
                   GithubApiUrlQueryBuilder.urlQuery { param("state", state); param("assignee", assignee); param(pagination) }))

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubIssue>(url).withOperationName("get issues in repository")

      @JvmStatic
      fun get(server: GithubServerPath, username: String, repoName: String, id: String) =
        Get.Optional.json<GithubIssue>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, "/", id))

      @JvmStatic
      fun updateState(server: GithubServerPath, username: String, repoName: String, id: String, open: Boolean) =
        Patch.json<GithubIssue>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, "/", id),
                                GithubChangeIssueStateRequest(if (open) "open" else "closed"))

      @JvmStatic
      fun updateAssignees(server: GithubServerPath, username: String, repoName: String, id: String, assignees: Collection<String>) =
        Patch.json<GithubIssue>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, "/", id),
                                GithubAssigneesCollectionRequest(assignees))

      object Comments : Entity("/comments") {
        @JvmStatic
        fun create(server: GithubServerPath, username: String, repoName: String, issueId: String, body: String) =
          Post.json<GithubIssueComment>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", Issues.urlSuffix, "/", issueId, urlSuffix),
                                        GithubCreateIssueCommentRequest(body))

        @JvmStatic
        fun pages(server: GithubServerPath, username: String, repoName: String, issueId: String) =
          GithubApiPagesLoader.Request(get(server, username, repoName, issueId), ::get)

        @JvmStatic
        fun pages(url: String) = GithubApiPagesLoader.Request(get(url), ::get)

        @JvmStatic
        fun get(server: GithubServerPath, username: String, repoName: String, issueId: String,
                pagination: GithubRequestPagination? = null) =
          get(getUrl(server, Repos.urlSuffix, "/$username/$repoName", Issues.urlSuffix, "/", issueId, urlSuffix,
                     GithubApiUrlQueryBuilder.urlQuery { param(pagination) }))

        @JvmStatic
        fun get(url: String) = Get.jsonPage<GithubIssueCommentWithHtml>(url, GithubApiContentHelper.V3_HTML_JSON_MIME_TYPE)
          .withOperationName("get comments for issue")
      }

      object Labels : Entity("/labels") {
        @JvmStatic
        fun replace(server: GithubServerPath, username: String, repoName: String, issueId: String, labels: Collection<String>) =
          Put.jsonList<GithubIssueLabel>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", Issues.urlSuffix, "/", issueId, urlSuffix),
                                         GithubLabelsCollectionRequest(labels))
      }
    }

    object PullRequests : Entity("/pulls") {
      @JvmStatic
      fun get(url: String) = Get.json<GithubPullRequestDetailed>(url).withOperationName("get pull request")

      @JvmStatic
      fun getHtml(serverPath: GithubServerPath, username: String, repoName: String, number: Long) =
        getHtml(getUrl(serverPath, Repos.urlSuffix, "/$username/$repoName", urlSuffix, "/$number"))

      @JvmStatic
      fun getHtml(url: String) = Get.json<GithubPullRequestDetailedWithHtml>(url, GithubApiContentHelper.V3_HTML_JSON_MIME_TYPE)
        .withOperationName("get pull request")

      @JvmStatic
      fun getDiff(serverPath: GithubServerPath, username: String, repoName: String, number: Long) =
        object : Get<String>(getUrl(serverPath, Repos.urlSuffix, "/$username/$repoName", urlSuffix, "/$number"),
                             GithubApiContentHelper.V3_DIFF_JSON_MIME_TYPE) {
          override fun extractResult(response: GithubApiResponse): String {
            return response.handleBody(ThrowableConvertor {
              StreamUtil.readText(it, Charsets.UTF_8)
            })
          }
        }.withOperationName("get pull request diff file")

      @JvmStatic
      fun create(server: GithubServerPath,
                 username: String, repoName: String,
                 title: String, description: String, head: String, base: String) =
        Post.json<GithubPullRequestDetailed>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix),
                                             GithubPullRequestRequest(title, description, head, base))
          .withOperationName("create pull request in $username/$repoName")

      @JvmStatic
      fun update(serverPath: GithubServerPath, username: String, repoName: String, number: Long,
                 title: String? = null,
                 body: String? = null,
                 state: GithubIssueState? = null,
                 base: String? = null,
                 maintainerCanModify: Boolean? = null) =
        Patch.json<GithubPullRequestDetailed>(getUrl(serverPath, Repos.urlSuffix, "/$username/$repoName", urlSuffix, "/$number"),
                                              GithubPullUpdateRequest(title, body, state, base, maintainerCanModify))
          .withOperationName("update pull request $number")

      @JvmStatic
      fun update(url: String,
                 title: String? = null,
                 body: String? = null,
                 state: GithubIssueState? = null,
                 base: String? = null,
                 maintainerCanModify: Boolean? = null) =
        Patch.json<GithubPullRequestDetailed>(url, GithubPullUpdateRequest(title, body, state, base, maintainerCanModify))
          .withOperationName("update pull request")

      @JvmStatic
      fun merge(pullRequest: GithubPullRequest, commitSubject: String, commitBody: String, headSha: String) =
        Put.json<Unit>(getMergeUrl(pullRequest),
                       GithubPullRequestMergeRequest(commitSubject, commitBody, headSha, GithubPullRequestMergeMethod.merge))
          .withOperationName("merge pull request ${pullRequest.number}")

      @JvmStatic
      fun squashMerge(pullRequest: GithubPullRequest, commitSubject: String, commitBody: String, headSha: String) =
        Put.json<Unit>(getMergeUrl(pullRequest),
                       GithubPullRequestMergeRequest(commitSubject, commitBody, headSha, GithubPullRequestMergeMethod.squash))
          .withOperationName("squash and merge pull request ${pullRequest.number}")

      @JvmStatic
      fun rebaseMerge(pullRequest: GithubPullRequest, headSha: String) =
        Put.json<Unit>(getMergeUrl(pullRequest),
                       GithubPullRequestMergeRebaseRequest(headSha))
          .withOperationName("rebase and merge pull request ${pullRequest.number}")

      private fun getMergeUrl(pullRequest: GithubPullRequest) = pullRequest.url + "/merge"

      @JvmStatic
      fun getListETag(server: GithubServerPath, repoPath: GithubFullPath) =
        object : Get<String?>(getUrl(server, Repos.urlSuffix, "/${repoPath.fullName}", urlSuffix,
                                     GithubApiUrlQueryBuilder.urlQuery { param(GithubRequestPagination(pageSize = 1)) })) {

          override fun extractResult(response: GithubApiResponse) = response.findHeader("ETag")
        }.withOperationName("get pull request list ETag")

      object Reviewers : Entity("/requested_reviewers") {
        @JvmStatic
        fun add(server: GithubServerPath, username: String, repoName: String, number: Long, reviewers: Collection<String>) =
          Post.json<Unit>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", PullRequests.urlSuffix, "/$number", urlSuffix),
                          GithubReviewersCollectionRequest(reviewers, listOf<String>()))

        @JvmStatic
        fun remove(server: GithubServerPath, username: String, repoName: String, number: Long, reviewers: Collection<String>) =
          Delete.json<Unit>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", PullRequests.urlSuffix, "/$number", urlSuffix),
                            GithubReviewersCollectionRequest(reviewers, listOf<String>()))
      }

      object Commits : Entity("/commits") {
        @JvmStatic
        fun pages(server: GithubServerPath, username: String, repoName: String, number: Long) =
          GithubApiPagesLoader.Request(get(server, username, repoName, number), ::get)

        @JvmStatic
        fun pages(url: String) = GithubApiPagesLoader.Request(get(url), ::get)

        @JvmStatic
        fun get(server: GithubServerPath, username: String, repoName: String, number: Long,
                pagination: GithubRequestPagination? = null) =
          get(getUrl(server, Repos.urlSuffix, "/$username/$repoName", PullRequests.urlSuffix, "/$number", urlSuffix,
                     GithubApiUrlQueryBuilder.urlQuery { param(pagination) }))

        @JvmStatic
        fun get(url: String) = Get.jsonPage<GithubCommit>(url)
          .withOperationName("get commits for pull request")
      }

      object Comments : Entity("/comments") {
        @JvmStatic
        fun pages(server: GithubServerPath, username: String, repoName: String, number: Long) =
          GithubApiPagesLoader.Request(get(server, username, repoName, number), ::get)

        @JvmStatic
        fun pages(url: String) = GithubApiPagesLoader.Request(get(url), ::get)

        @JvmStatic
        fun get(server: GithubServerPath, username: String, repoName: String, number: Long,
                pagination: GithubRequestPagination? = null) =
          get(getUrl(server, Repos.urlSuffix, "/$username/$repoName", PullRequests.urlSuffix, "/$number", urlSuffix,
                     GithubApiUrlQueryBuilder.urlQuery { param(pagination) }))

        @JvmStatic
        fun get(url: String) = Get.jsonPage<GithubPullRequestCommentWithHtml>(url, GithubApiContentHelper.V3_HTML_JSON_MIME_TYPE)
          .withOperationName("get comments for pull request")
      }
    }
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
    fun delete(server: GithubServerPath, id: String) = Delete.json<Unit>(getUrl(server, urlSuffix, "/$id"))
      .withOperationName("delete gist $id")
  }

  object Search : Entity("/search") {
    object Issues : Entity("/issues") {
      @JvmStatic
      fun pages(server: GithubServerPath, repoPath: GithubFullPath?, state: String?, assignee: String?, query: String?) =
        GithubApiPagesLoader.Request(get(server, repoPath, state, assignee, query), ::get)

      @JvmStatic
      fun get(server: GithubServerPath, repoPath: GithubFullPath?, state: String?, assignee: String?, query: String?,
              pagination: GithubRequestPagination? = null) =
        get(getUrl(server, Search.urlSuffix, urlSuffix,
                   GithubApiUrlQueryBuilder.urlQuery {
                     param("q", GithubApiSearchQueryBuilder.searchQuery {
                       qualifier("repo", repoPath?.fullName.orEmpty())
                       qualifier("state", state)
                       qualifier("assignee", assignee)
                       query(query)
                     })
                     param(pagination)
                   }))

      @JvmStatic
      fun get(server: GithubServerPath, query: String, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, Search.urlSuffix, urlSuffix,
                   GithubApiUrlQueryBuilder.urlQuery {
                     param("q", query)
                     param(pagination)
                   }))


      @JvmStatic
      fun get(url: String) = Get.jsonSearchPage<GithubSearchedIssue>(url).withOperationName("search issues in repository")
    }
  }

  object Auth : Entity("/authorizations") {
    @JvmStatic
    fun create(server: GithubServerPath, scopes: List<String>, note: String) =
      Post.json<GithubAuthorization>(getUrl(server, urlSuffix), GithubAuthorizationCreateRequest(scopes, note, null))
        .withOperationName("create authorization $note")

    @JvmStatic
    fun get(server: GithubServerPath, pagination: GithubRequestPagination? = null) =
      get(getUrl(server, urlSuffix,
                 GithubApiUrlQueryBuilder.urlQuery { param(pagination) }))

    @JvmStatic
    fun get(url: String) = Get.jsonPage<GithubAuthorization>(url)
      .withOperationName("get authorizations")

    @JvmStatic
    fun pages(server: GithubServerPath, pagination: GithubRequestPagination? = null) =
      GithubApiPagesLoader.Request(get(server, pagination), ::get)
  }

  abstract class Entity(val urlSuffix: String)

  private fun getUrl(server: GithubServerPath, suffix: String) = server.toApiUrl() + suffix

  fun getUrl(server: GithubServerPath, vararg suffixes: String) = StringBuilder(server.toApiUrl()).append(*suffixes).toString()

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