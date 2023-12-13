// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.platform.templates.github.GithubTagInfo
import com.intellij.util.ThrowableConvertor
import org.jetbrains.plugins.github.api.GithubApiRequest.*
import org.jetbrains.plugins.github.api.data.*
import org.jetbrains.plugins.github.api.data.request.*
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.api.util.GithubApiSearchQueryBuilder
import org.jetbrains.plugins.github.api.util.GithubApiUrlQueryBuilder
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery
import java.awt.image.BufferedImage

/**
 * Collection of factory methods for API requests used in plugin
 * TODO: improve url building (DSL?)
 */
object GithubApiRequests {

  @JvmStatic
  fun getBytes(url: String): GithubApiRequest<ByteArray> = object : Get<ByteArray>(url) {
    override fun extractResult(response: GithubApiResponse): ByteArray {
      return response.handleBody(ThrowableConvertor {
        it.readAllBytes()
      })
    }
  }

  object CurrentUser : Entity("/user") {
    @JvmStatic
    fun get(server: GithubServerPath) = get(getUrl(server, urlSuffix))

    @JvmStatic
    fun get(url: String) = Get.json<GithubAuthenticatedUser>(url).withOperationName("get profile information")

    @JvmStatic
    fun getAvatar(url: String): GithubApiRequest<BufferedImage> = object : Get<BufferedImage>(url) {
      override fun extractResult(response: GithubApiResponse): BufferedImage {
        return response.handleBody(ThrowableConvertor {
          GithubApiContentHelper.loadImage(it)
        })
      }
    }.withOperationName("get profile avatar")

    object Repos : Entity("/repos") {
      @JvmOverloads
      @JvmStatic
      fun pages(server: GithubServerPath,
                type: Type = Type.DEFAULT,
                visibility: Visibility = Visibility.DEFAULT,
                affiliation: Affiliation = Affiliation.DEFAULT,
                pagination: GithubRequestPagination? = null) =
        GithubApiPagesLoader.Request(get(server, type, visibility, affiliation, pagination), ::get)

      @JvmOverloads
      @JvmStatic
      fun get(server: GithubServerPath,
              type: Type = Type.DEFAULT,
              visibility: Visibility = Visibility.DEFAULT,
              affiliation: Affiliation = Affiliation.DEFAULT,
              pagination: GithubRequestPagination? = null): GithubApiRequest<GithubResponsePage<GithubRepo>> {
        if (type != Type.DEFAULT && (visibility != Visibility.DEFAULT || affiliation != Affiliation.DEFAULT)) {
          throw IllegalArgumentException("Param 'type' should not be used together with 'visibility' or 'affiliation'")
        }

        return get(getUrl(server, CurrentUser.urlSuffix, urlSuffix,
                          getQuery(type.toString(), visibility.toString(), affiliation.toString(), pagination?.toString().orEmpty())))
      }

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubRepo>(url).withOperationName("get user repositories")

      @JvmStatic
      fun create(server: GithubServerPath, name: String, description: String, private: Boolean, autoInit: Boolean? = null) =
        Post.json<GithubRepo>(getUrl(server, CurrentUser.urlSuffix, urlSuffix),
                              GithubRepoRequest(name, description, private, autoInit))
          .withOperationName("create user repository")
    }

    object Orgs : Entity("/orgs") {
      @JvmOverloads
      @JvmStatic
      fun pages(server: GithubServerPath, pagination: GithubRequestPagination? = null) =
        GithubApiPagesLoader.Request(get(server, pagination), ::get)

      fun get(server: GithubServerPath, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, CurrentUser.urlSuffix, urlSuffix, getQuery(pagination?.toString().orEmpty())))

      fun get(url: String) = Get.jsonPage<GithubOrg>(url).withOperationName("get user organizations")
    }
  }

  object Organisations : Entity("/orgs") {

    object Repos : Entity("/repos") {
      @JvmStatic
      fun pages(server: GithubServerPath, organisation: String, pagination: GithubRequestPagination? = null) =
        GithubApiPagesLoader.Request(get(server, organisation, pagination), ::get)

      @JvmOverloads
      @JvmStatic
      fun get(server: GithubServerPath, organisation: String, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, Organisations.urlSuffix, "/", organisation, urlSuffix, getQuery(pagination?.toString().orEmpty())))

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
    fun get(url: String) = Get.Optional.json<GithubRepoDetailed>(url).withOperationName("get information for repository $url")

    @JvmStatic
    fun delete(server: GithubServerPath, username: String, repoName: String) =
      delete(getUrl(server, urlSuffix, "/$username/$repoName")).withOperationName("delete repository $username/$repoName")

    @JvmStatic
    fun delete(url: String) = Delete.json<Unit>(url).withOperationName("delete repository at $url")

    object Content : Entity("/contents") {

      @JvmOverloads
      @JvmStatic
      fun list(server: GithubServerPath, username: String, repoName: String, path: String, ref: String? = null, pagination: GithubRequestPagination) =
          list(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, "/$path", getQuery(if (ref == null) "" else "ref=$ref", pagination.toString())))
      @JvmOverloads
      @JvmStatic
      fun get(server: GithubServerPath, username: String, repoName: String, path: String, ref: String? = null) =
          get (getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, "/$path", getQuery(if (ref == null) "" else "ref=$ref")))
      @JvmStatic
      fun list(url: String) = Get.jsonPage<GithubContent>(url).withOperationName("get content")

      @JvmStatic
      fun get(url: String) = Get.json<GithubContent>(url).withOperationName("get file")

    }

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

    object Tags : Entity("/tags") {
      @JvmStatic
      fun pages(server: GithubServerPath, username: String, repoName: String) =
        GithubApiPagesLoader.Request(get(server, username, repoName), ::get)

      @JvmOverloads
      @JvmStatic
      fun get(server: GithubServerPath, username: String, repoName: String, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, getQuery(pagination?.toString().orEmpty())))

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubTagInfo>(url).withOperationName("get tags")
    }

    object Commits : Entity("/commits") {

      @JvmStatic
      fun compare(repository: GHRepositoryCoordinates, refA: String, refB: String) =
        Get.json<GHCommitsCompareResult>(getUrl(repository, "/compare/$refA...$refB")).withOperationName("compare refs")

      @JvmStatic
      fun getDiff(repository: GHRepositoryCoordinates, ref: String) =
        object : Get<String>(getUrl(repository, urlSuffix, "/$ref"),
                             GithubApiContentHelper.V3_DIFF_JSON_MIME_TYPE) {
          override fun extractResult(response: GithubApiResponse): String {
            return response.handleBody(ThrowableConvertor {
              it.reader().use { it.readText() }
            })
          }
        }.withOperationName("get diff for ref")

      @JvmStatic
      fun getDiff(repository: GHRepositoryCoordinates, refA: String, refB: String) =
        object : Get<String>(getUrl(repository, "/compare/$refA...$refB"),
                             GithubApiContentHelper.V3_DIFF_JSON_MIME_TYPE) {
          override fun extractResult(response: GithubApiResponse): String {
            return response.handleBody(ThrowableConvertor {
              it.reader().use { it.readText() }
            })
          }
        }.withOperationName("get diff between refs")
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
        fun create(repository: GHRepositoryCoordinates, issueId: Long, body: String) =
          create(repository.serverPath, repository.repositoryPath.owner, repository.repositoryPath.repository, issueId.toString(), body)

        @JvmStatic
        fun create(server: GithubServerPath, username: String, repoName: String, issueId: String, body: String) =
          Post.json<GithubIssueCommentWithHtml>(
            getUrl(server, Repos.urlSuffix, "/$username/$repoName", Issues.urlSuffix, "/", issueId, urlSuffix),
            GithubCreateIssueCommentRequest(body),
            GithubApiContentHelper.V3_HTML_JSON_MIME_TYPE)

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
      fun update(serverPath: GithubServerPath, username: String, repoName: String, number: Long,
                 title: String? = null,
                 body: String? = null,
                 state: GithubIssueState? = null,
                 base: String? = null,
                 maintainerCanModify: Boolean? = null) =
        Patch.json<Any>(getUrl(serverPath, Repos.urlSuffix, "/$username/$repoName", urlSuffix, "/$number"),
                        GithubPullUpdateRequest(title, body, state, base, maintainerCanModify))
          .withOperationName("update pull request $number")

      @JvmStatic
      fun merge(server: GithubServerPath, repoPath: GHRepositoryPath, number: Long,
                commitSubject: String, commitBody: String, headSha: String) =
        Put.json<Unit>(getUrl(server, Repos.urlSuffix, "/$repoPath", urlSuffix, "/$number", "/merge"),
                       GithubPullRequestMergeRequest(commitSubject, commitBody, headSha, GithubPullRequestMergeMethod.merge))
          .withOperationName("merge pull request ${number}")

      @JvmStatic
      fun squashMerge(server: GithubServerPath, repoPath: GHRepositoryPath, number: Long,
                      commitSubject: String, commitBody: String, headSha: String) =
        Put.json<Unit>(getUrl(server, Repos.urlSuffix, "/$repoPath", urlSuffix, "/$number", "/merge"),
                       GithubPullRequestMergeRequest(commitSubject, commitBody, headSha, GithubPullRequestMergeMethod.squash))
          .withOperationName("squash and merge pull request ${number}")

      @JvmStatic
      fun rebaseMerge(server: GithubServerPath, repoPath: GHRepositoryPath, number: Long,
                      headSha: String) =
        Put.json<Unit>(getUrl(server, Repos.urlSuffix, "/$repoPath", urlSuffix, "/$number", "/merge"),
                       GithubPullRequestMergeRebaseRequest(headSha))
          .withOperationName("rebase and merge pull request ${number}")

      @JvmStatic
      fun getListETag(server: GithubServerPath, repoPath: GHRepositoryPath) =
        object : Get<String?>(getUrl(server, Repos.urlSuffix, "/$repoPath", urlSuffix,
                                     GithubApiUrlQueryBuilder.urlQuery { param(GithubRequestPagination(pageSize = 1)) })) {
          override fun extractResult(response: GithubApiResponse) = response.findHeader("ETag")
        }.withOperationName("get pull request list ETag")

      @JvmStatic
      fun getDiff(repository: GHRepositoryCoordinates, number: Long) =
        object : Get<String>(getUrl(repository, urlSuffix, "/$number"),
                             GithubApiContentHelper.V3_DIFF_JSON_MIME_TYPE) {
          override fun extractResult(response: GithubApiResponse): String {
            return response.handleBody(ThrowableConvertor {
              it.reader().use { it.readText() }
            })
          }
        }.withOperationName("get diff of a PR")

      object Reviewers : Entity("/requested_reviewers") {
        @JvmStatic
        fun add(server: GithubServerPath, username: String, repoName: String, number: Long,
                reviewers: Collection<String>, teamReviewers: List<String>) =
          Post.json<Unit>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", PullRequests.urlSuffix, "/$number", urlSuffix),
                          GithubReviewersCollectionRequest(reviewers, teamReviewers))

        @JvmStatic
        fun remove(server: GithubServerPath, username: String, repoName: String, number: Long,
                   reviewers: Collection<String>, teamReviewers: List<String>) =
          Delete.json<Unit>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", PullRequests.urlSuffix, "/$number", urlSuffix),
                            GithubReviewersCollectionRequest(reviewers, teamReviewers))
      }
    }
  }

  object Gists : Entity("/gists") {
    @JvmStatic
    fun create(server: GithubServerPath,
               contents: List<GithubGistRequest.FileContent>, description: String, public: Boolean) =
      Post.json<GithubGist>(getUrl(server, urlSuffix),
                            GithubGistRequest(contents, description, public))
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
      fun pages(server: GithubServerPath, repoPath: GHRepositoryPath?, state: String?, assignee: String?, query: String?) =
        GithubApiPagesLoader.Request(get(server, repoPath, state, assignee, query), ::get)

      @JvmStatic
      fun get(server: GithubServerPath, repoPath: GHRepositoryPath?, state: String?, assignee: String?, query: String?,
              pagination: GithubRequestPagination? = null) =
        get(getUrl(server, Search.urlSuffix, urlSuffix,
                   GithubApiUrlQueryBuilder.urlQuery {
                     param("q", GithubApiSearchQueryBuilder.searchQuery {
                       term(GHPRSearchQuery.QualifierName.repo.createTerm(repoPath?.toString().orEmpty()))
                       term(GHPRSearchQuery.QualifierName.state.createTerm(state.orEmpty()))
                       term(GHPRSearchQuery.QualifierName.assignee.createTerm(assignee.orEmpty()))
                       query(query)
                     })
                     param(pagination)
                   }))

      @JvmStatic
      fun get(url: String) = Get.jsonSearchPage<GithubSearchedIssue>(url).withOperationName("search issues in repository")
    }
  }

  abstract class Entity(val urlSuffix: String)

  private fun getUrl(server: GithubServerPath, suffix: String) = server.toApiUrl() + suffix

  private fun getUrl(repository: GHRepositoryCoordinates, vararg suffixes: String) =
    getUrl(repository.serverPath, Repos.urlSuffix, "/", repository.repositoryPath.toString(), *suffixes)

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