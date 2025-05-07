// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

import com.intellij.collaboration.api.util.LinkHttpHeaderValue
import com.intellij.platform.templates.github.GithubTagInfo
import com.intellij.util.ThrowableConvertor
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.github.api.GithubApiRequest.*
import org.jetbrains.plugins.github.api.data.*
import org.jetbrains.plugins.github.api.data.commit.GHCommitFile
import org.jetbrains.plugins.github.api.data.commit.GHCommitFiles
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRestIdOnly
import org.jetbrains.plugins.github.api.data.request.*
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.api.util.GithubApiSearchQueryBuilder.Companion.searchQuery
import org.jetbrains.plugins.github.api.util.GithubApiUrlQueryBuilder.Companion.paginationQuery
import org.jetbrains.plugins.github.api.util.GithubApiUrlQueryBuilder.Companion.urlQuery
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery.QualifierName
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
    fun get(url: String) = Get.json<GithubAuthenticatedUser>(url)
      .withOperation(GithubApiRequestOperation.RestGetAuthenticatedUser)
      .withOperationName("get profile information")

    @JvmStatic
    fun getAvatar(url: String): GithubApiRequest<BufferedImage> = object : Get<BufferedImage>(url) {
      override fun extractResult(response: GithubApiResponse): BufferedImage {
        return response.handleBody(ThrowableConvertor {
          GithubApiContentHelper.loadImage(it)
        })
      }
    }
      .withOperation(GithubApiRequestOperation.RestGetAvatar)
      .withOperationName("get profile avatar")

    object Repos : Entity("/repos") {
      @JvmOverloads
      @JvmStatic
      fun pages(
        server: GithubServerPath,
        type: Type? = null,
        visibility: Visibility? = null,
        affiliations: Set<Affiliation>? = null,
        pagination: GithubRequestPagination? = null,
      ) =
        GithubApiPagesLoader.Request(get(server, type, visibility, affiliations, pagination), ::get)

      @JvmOverloads
      @JvmStatic
      fun get(
        server: GithubServerPath,
        type: Type? = null,
        visibility: Visibility? = null,
        affiliations: Set<Affiliation>? = null,
        pagination: GithubRequestPagination? = null,
      ): GithubApiRequest<GithubResponsePage<GithubRepo>> {
        if (type != null && (visibility != null || affiliations != null)) {
          throw IllegalArgumentException("Param 'type' should not be used together with 'visibility' or 'affiliation'")
        }

        return get(getUrl(server, CurrentUser.urlSuffix, urlSuffix,
                          urlQuery {
                            param(Type.KEY, type?.value)
                            param(Visibility.KEY, visibility?.value)
                            param(Affiliation.KEY, affiliations?.let(Affiliation::combine))
                            param(pagination)
                          }))
      }

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubRepo>(url)
        .withOperation(GithubApiRequestOperation.RestGetRepositoriesForUser)
        .withOperationName("get user repositories")

      @JvmStatic
      fun create(server: GithubServerPath, name: String, description: String, private: Boolean, autoInit: Boolean? = null) =
        Post.json<GithubRepo>(getUrl(server, CurrentUser.urlSuffix, urlSuffix),
                              GithubRepoRequest(name, description, private, autoInit))
          .withOperation(GithubApiRequestOperation.RestCreateRepository)
          .withOperationName("create user repository")
    }

    object Orgs : Entity("/orgs") {
      @JvmOverloads
      @JvmStatic
      fun pages(server: GithubServerPath, pagination: GithubRequestPagination? = null) =
        GithubApiPagesLoader.Request(get(server, pagination), ::get)

      fun get(server: GithubServerPath, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, CurrentUser.urlSuffix, urlSuffix, paginationQuery(pagination)))

      fun get(url: String) = Get.jsonPage<GithubOrg>(url)
        .withOperation(GithubApiRequestOperation.RestGetOrganizations)
        .withOperationName("get user organizations")
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
        get(getUrl(server, Organisations.urlSuffix, "/", organisation, urlSuffix, paginationQuery(pagination)))

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubRepo>(url)
        .withOperation(GithubApiRequestOperation.RestGetRepositoriesForOrganization)
        .withOperationName("get organisation repositories")

      @JvmStatic
      fun create(server: GithubServerPath, organisation: String, name: String, description: String, private: Boolean) =
        Post.json<GithubRepo>(getUrl(server, Organisations.urlSuffix, "/", organisation, urlSuffix),
                              GithubRepoRequest(name, description, private, null))
          .withOperation(GithubApiRequestOperation.RestCreateRepositoryInOrganization)
          .withOperationName("create organisation repository")
    }
  }

  object Repos : Entity("/repos") {
    @JvmStatic
    fun get(server: GithubServerPath, username: String, repoName: String) =
      Get.Optional.json<GithubRepoDetailed>(getUrl(server, urlSuffix, "/$username/$repoName"))
        .withOperation(GithubApiRequestOperation.RestGetRepository)
        .withOperationName("get information for repository $username/$repoName")

    @JvmStatic
    @VisibleForTesting
    fun get(url: String) = Get.Optional.json<GithubRepoDetailed>(url)
      .withOperation(GithubApiRequestOperation.RestGetRepositoryByUrl)
      .withOperationName("get information for repository $url")

    @JvmStatic
    fun delete(server: GithubServerPath, username: String, repoName: String) =
      delete(getUrl(server, urlSuffix, "/$username/$repoName"))
        .withOperation(GithubApiRequestOperation.RestDeleteRepository)
        .withOperationName("delete repository $username/$repoName")

    @JvmStatic
    @VisibleForTesting
    fun delete(url: String) = Delete.json<Unit>(url)
      .withOperation(GithubApiRequestOperation.RestDeleteRepositoryByUrl)
      .withOperationName("delete repository at $url")

    object Content : Entity("/contents") {

      @JvmOverloads
      @JvmStatic
      fun list(server: GithubServerPath, username: String, repoName: String, path: String, ref: String? = null, pagination: GithubRequestPagination) =
        list(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, "/$path", urlQuery {
            if (ref != null) {
              param("ref", ref)
            }
            param(pagination)
          }))
      @JvmOverloads
      @JvmStatic
      fun get(server: GithubServerPath, username: String, repoName: String, path: String, ref: String? = null) =
        get(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, "/$path", urlQuery {
            if (ref != null) {
              param("ref", ref)
            }
          }))
      @JvmStatic
      fun list(url: String) = Get.jsonPage<GithubContent>(url)
        .withOperation(GithubApiRequestOperation.RestGetContents)
        .withOperationName("get content")

      @JvmStatic
      fun get(url: String) = Get.json<GithubContent>(url)
        .withOperation(GithubApiRequestOperation.RestGetContent)
        .withOperationName("get file")

    }

    // used externally
    object Branches : Entity("/branches") {
      @JvmStatic
      fun pages(server: GithubServerPath, username: String, repoName: String) =
        GithubApiPagesLoader.Request(get(server, username, repoName), ::get)

      @JvmOverloads
      @JvmStatic
      fun get(server: GithubServerPath, username: String, repoName: String, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, paginationQuery(pagination)))

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubBranch>(url)
        .withOperation(GithubApiRequestOperation.RestGetBranches)
        .withOperationName("get branches")
    }

    object Tags : Entity("/tags") {
      @JvmStatic
      fun pages(server: GithubServerPath, username: String, repoName: String) =
        GithubApiPagesLoader.Request(get(server, username, repoName), ::get)

      @JvmOverloads
      @JvmStatic
      fun get(server: GithubServerPath, username: String, repoName: String, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, paginationQuery(pagination)))

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubTagInfo>(url)
        .withOperation(GithubApiRequestOperation.RestGetTags)
        .withOperationName("get tags")
    }

    object Commits : Entity("/commits") {

      @JvmStatic
      fun compare(repository: GHRepositoryCoordinates, refA: String, refB: String) =
        Get.json<GHCommitsCompareResult>(getUrl(repository, "/compare/$refA...$refB"))
          .withOperation(GithubApiRequestOperation.RestGetRefComparison)
          .withOperationName("compare refs")

      @JvmStatic
      fun getDiffFiles(repository: GHRepositoryCoordinates, ref: String): GithubApiRequest<GithubResponsePage<GHCommitFile>> =
        getDiffFiles(getUrl(repository, urlSuffix, "/$ref"))

      @JvmStatic
      fun getDiffFiles(url: String): GithubApiRequest<GithubResponsePage<GHCommitFile>> =
        object : Get<GithubResponsePage<GHCommitFile>>(url) {
          override fun extractResult(response: GithubApiResponse): GithubResponsePage<GHCommitFile> {
            val list = response.readBody(ThrowableConvertor { GithubApiContentHelper.readJsonObject(it, GHCommitFiles::class.java) })
              .files
            val linkHeader = response.findHeader(LinkHttpHeaderValue.HEADER_NAME)?.let(LinkHttpHeaderValue::parse)
            return GithubResponsePage(list, linkHeader)
          }
        }
          .withOperation(GithubApiRequestOperation.RestGetCommitDiffFiles)
          .withOperationName("get files for ref")

      @JvmStatic
      fun getDiff(repository: GHRepositoryCoordinates, refA: String, refB: String) =
        object : Get<String>(getUrl(repository, "/compare/$refA...$refB"),
                             GithubApiContentHelper.V3_DIFF_JSON_MIME_TYPE) {
          override fun extractResult(response: GithubApiResponse): String {
            return response.handleBody(ThrowableConvertor {
              it.reader().use { it.readText() }
            })
          }
        }
          .withOperation(GithubApiRequestOperation.RestGetCommitDiff)
          .withOperationName("get diff between refs")
    }

    object Forks : Entity("/forks") {

      @JvmStatic
      fun create(server: GithubServerPath, username: String, repoName: String) =
        Post.json<GithubRepo>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix), Any())
          .withOperation(GithubApiRequestOperation.RestCreateFork)
          .withOperationName("fork repository $username/$repoName for current user")

      @JvmStatic
      fun pages(server: GithubServerPath, username: String, repoName: String) =
        GithubApiPagesLoader.Request(get(server, username, repoName), ::get)

      @JvmOverloads
      @JvmStatic
      fun get(server: GithubServerPath, username: String, repoName: String, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, paginationQuery(pagination)))

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubRepo>(url)
        .withOperation(GithubApiRequestOperation.RestGetForks)
        .withOperationName("get forks")
    }

    object Assignees : Entity("/assignees") {

      @JvmStatic
      fun pages(server: GithubServerPath, username: String, repoName: String) =
        GithubApiPagesLoader.Request(get(server, username, repoName), ::get)

      @JvmOverloads
      @JvmStatic
      fun get(server: GithubServerPath, username: String, repoName: String, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, paginationQuery(pagination)))

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubUser>(url)
        .withOperation(GithubApiRequestOperation.RestGetRepositoryAssignees)
        .withOperationName("get assignees")
    }

    object Labels : Entity("/labels") {

      @JvmStatic
      fun pages(server: GithubServerPath, username: String, repoName: String) =
        GithubApiPagesLoader.Request(get(server, username, repoName), ::get)

      @JvmOverloads
      @JvmStatic
      fun get(server: GithubServerPath, username: String, repoName: String, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, paginationQuery(pagination)))

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubIssueLabel>(url)
        .withOperation(GithubApiRequestOperation.RestGetRepositoryLabels)
        .withOperationName("get labels")
    }

    object Collaborators : Entity("/collaborators") {

      @JvmStatic
      fun pages(server: GithubServerPath, username: String, repoName: String) =
        GithubApiPagesLoader.Request(get(server, username, repoName), ::get)

      @JvmOverloads
      @JvmStatic
      fun get(server: GithubServerPath, username: String, repoName: String, pagination: GithubRequestPagination? = null) =
        get(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, paginationQuery(pagination)))

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubUserWithPermissions>(url)
        .withOperation(GithubApiRequestOperation.RestGetRepositoryCollaborators)
        .withOperationName("get collaborators")

      @JvmStatic
      fun add(server: GithubServerPath, username: String, repoName: String, collaborator: String) =
        Put.json<Unit>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, "/", collaborator))
          .withOperation(GithubApiRequestOperation.RestAddCollaboratorToRepository)
          .withOperationName("add collaborator")
    }

    object Issues : Entity("/issues") {

      @JvmStatic
      fun create(
        server: GithubServerPath,
        username: String,
        repoName: String,
        title: String,
        body: String? = null,
        milestone: Long? = null,
        labels: List<String>? = null,
        assignees: List<String>? = null,
      ) =
        Post.json<GithubIssue>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix),
                               GithubCreateIssueRequest(title, body, milestone, labels, assignees)).apply {
          withOperation(GithubApiRequestOperation.RestCreateIssue)
          withOperationName("create issue")
        }

      @JvmStatic
      fun pages(
        server: GithubServerPath, username: String, repoName: String,
        state: String? = null, assignee: String? = null,
      ) = GithubApiPagesLoader.Request(get(server, username, repoName,
                                           state, assignee), ::get)

      @JvmStatic
      fun get(
        server: GithubServerPath, username: String, repoName: String,
        state: String? = null, assignee: String? = null, pagination: GithubRequestPagination? = null,
      ) =
        get(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix,
                   urlQuery { param("state", state); param("assignee", assignee); param(pagination) }))

      @JvmStatic
      fun get(url: String) = Get.jsonPage<GithubIssue>(url)
        .withOperation(GithubApiRequestOperation.RestGetIssues)
        .withOperationName("get issues in repository")

      @JvmStatic
      fun get(server: GithubServerPath, username: String, repoName: String, id: String) =
        Get.Optional.json<GithubIssue>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, "/", id)).apply {
          withOperation(GithubApiRequestOperation.RestGetIssue)
          withOperationName("get issue")
        }

      @JvmStatic
      fun updateState(server: GithubServerPath, username: String, repoName: String, id: String, open: Boolean) =
        Patch.json<GithubIssue>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, "/", id),
                                GithubChangeIssueStateRequest(if (open) "open" else "closed"))
          .withOperation(GithubApiRequestOperation.RestUpdateIssueState)
          .withOperationName("update issue state")

      @JvmStatic
      fun updateAssignees(server: GithubServerPath, username: String, repoName: String, id: String, assignees: Collection<String>) =
        Patch.json<GithubIssue>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", urlSuffix, "/", id),
                                GithubAssigneesCollectionRequest(assignees))
          .withOperation(GithubApiRequestOperation.RestUpdateIssueAssignees)
          .withOperationName("update assignees for issue")

      object Comments : Entity("/comments") {
        @JvmStatic
        fun create(repository: GHRepositoryCoordinates, issueId: Long, body: String) =
          create(repository.serverPath, repository.repositoryPath.owner, repository.repositoryPath.repository, issueId.toString(), body)

        @JvmStatic
        fun create(server: GithubServerPath, username: String, repoName: String, issueId: String, body: String): Post<GithubIssueCommentWithHtml> =
          Post.json<GithubIssueCommentWithHtml>(
            getUrl(server, Repos.urlSuffix, "/$username/$repoName", Issues.urlSuffix, "/", issueId, urlSuffix),
            GithubCreateIssueCommentRequest(body),
            GithubApiContentHelper.V3_HTML_JSON_MIME_TYPE
          ).apply {
            withOperation(GithubApiRequestOperation.RestCreateIssueComment)
            withOperationName("create comment for issue")
          }

        @JvmStatic
        fun pages(server: GithubServerPath, username: String, repoName: String, issueId: String) =
          GithubApiPagesLoader.Request(get(server, username, repoName, issueId), ::get)

        @JvmStatic
        fun pages(url: String) = GithubApiPagesLoader.Request(get(url), ::get)

        @JvmStatic
        fun get(
          server: GithubServerPath, username: String, repoName: String, issueId: String,
          pagination: GithubRequestPagination? = null,
        ) =
          get(getUrl(server, Repos.urlSuffix, "/$username/$repoName", Issues.urlSuffix, "/", issueId, urlSuffix,
                     urlQuery { param(pagination) }))

        @JvmStatic
        fun get(url: String) = Get.jsonPage<GithubIssueCommentWithHtml>(url, GithubApiContentHelper.V3_HTML_JSON_MIME_TYPE)
          .withOperation(GithubApiRequestOperation.RestGetIssueComment)
          .withOperationName("get comments for issue")
      }

      object Labels : Entity("/labels") {
        @JvmStatic
        fun replace(server: GithubServerPath, username: String, repoName: String, issueId: String, labels: Collection<String>) =
          Put.jsonList<GithubIssueLabel>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", Issues.urlSuffix, "/", issueId, urlSuffix),
                                         GithubLabelsCollectionRequest(labels))
            .withOperation(GithubApiRequestOperation.RestUpdateIssueLabels)
            .withOperationName("replace issue labels")
      }
    }

    object PullRequests : Entity("/pulls") {

      @JvmStatic
      fun find(
        repository: GHRepositoryCoordinates,
        state: GithubIssueState? = null,
        baseRef: String? = null,
        headRef: String? = null,
      ): GithubApiRequest<GithubResponsePage<GHPullRequestRestIdOnly>> =
        Get.jsonPage<GHPullRequestRestIdOnly>(getUrl(repository, urlSuffix, urlQuery {
          param("state", state?.toString())
          param("base", baseRef)
          param("head", headRef)
        }))
          .withOperation(GithubApiRequestOperation.RestGetPullRequests)
          .withOperationName("find pull requests")

      @JvmStatic
      fun update(
        serverPath: GithubServerPath, username: String, repoName: String, number: Long,
        title: String? = null,
        body: String? = null,
        state: GithubIssueState? = null,
        base: String? = null,
        maintainerCanModify: Boolean? = null,
      ) =
        Patch.json<Any>(getUrl(serverPath, Repos.urlSuffix, "/$username/$repoName", urlSuffix, "/$number"),
                        GithubPullUpdateRequest(title, body, state, base, maintainerCanModify))
          .withOperation(GithubApiRequestOperation.RestUpdatePullRequest)
          .withOperationName("update pull request $number")

      @JvmStatic
      fun merge(
        server: GithubServerPath, repoPath: GHRepositoryPath, number: Long,
        commitSubject: String, commitBody: String, headSha: String,
      ) =
        Put.json<Unit>(getUrl(server, Repos.urlSuffix, "/$repoPath", urlSuffix, "/$number", "/merge"),
                       GithubPullRequestMergeRequest(commitSubject, commitBody, headSha, GithubPullRequestMergeMethod.merge))
          .withOperation(GithubApiRequestOperation.RestMergePullRequest)
          .withOperationName("merge pull request ${number}")

      @JvmStatic
      fun squashMerge(
        server: GithubServerPath, repoPath: GHRepositoryPath, number: Long,
        commitSubject: String, commitBody: String, headSha: String,
      ) =
        Put.json<Unit>(getUrl(server, Repos.urlSuffix, "/$repoPath", urlSuffix, "/$number", "/merge"),
                       GithubPullRequestMergeRequest(commitSubject, commitBody, headSha, GithubPullRequestMergeMethod.squash))
          .withOperation(GithubApiRequestOperation.RestSquashMergePullRequest)
          .withOperationName("squash and merge pull request ${number}")

      @JvmStatic
      fun rebaseMerge(
        server: GithubServerPath, repoPath: GHRepositoryPath, number: Long,
        headSha: String,
      ) =
        Put.json<Unit>(getUrl(server, Repos.urlSuffix, "/$repoPath", urlSuffix, "/$number", "/merge"),
                       GithubPullRequestMergeRebaseRequest(headSha))
          .withOperation(GithubApiRequestOperation.RestRebaseMergePullRequest)
          .withOperationName("rebase and merge pull request ${number}")

      @JvmStatic
      fun getListETag(server: GithubServerPath, repoPath: GHRepositoryPath) =
        object : Get<String?>(getUrl(server, Repos.urlSuffix, "/$repoPath", urlSuffix,
                                     urlQuery { param(GithubRequestPagination(pageSize = 1)) })) {
          override fun extractResult(response: GithubApiResponse) = response.findHeader("ETag")
        }
          .withOperation(GithubApiRequestOperation.RestGetPullRequestListETag)
          .withOperationName("get pull request list ETag")

      @JvmStatic
      fun getDiffFiles(repository: GHRepositoryCoordinates, id: GHPRIdentifier): GithubApiRequest<GithubResponsePage<GHCommitFile>> =
        getDiffFiles(getUrl(repository, urlSuffix, "/${id.number}", "/files"))

      @JvmStatic
      fun getDiffFiles(url: String): GithubApiRequest<GithubResponsePage<GHCommitFile>> =
        Get.jsonPage<GHCommitFile>(url)
          .withOperation(GithubApiRequestOperation.RestGetPullRequestDiffFiles)
          .withOperationName("get files for pull request")

      object Reviewers : Entity("/requested_reviewers") {
        @JvmStatic
        fun add(
          server: GithubServerPath, username: String, repoName: String, number: Long,
          reviewers: Collection<String>, teamReviewers: List<String>,
        ) =
          Post.json<Unit>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", PullRequests.urlSuffix, "/$number", urlSuffix),
                          GithubReviewersCollectionRequest(reviewers, teamReviewers))
            .withOperation(GithubApiRequestOperation.RestAddReviewerToPullRequest)
            .withOperationName("add reviewer")

        @JvmStatic
        fun remove(
          server: GithubServerPath, username: String, repoName: String, number: Long,
          reviewers: Collection<String>, teamReviewers: List<String>,
        ) =
          Delete.json<Unit>(getUrl(server, Repos.urlSuffix, "/$username/$repoName", PullRequests.urlSuffix, "/$number", urlSuffix),
                            GithubReviewersCollectionRequest(reviewers, teamReviewers))
            .withOperation(GithubApiRequestOperation.RestRemoveReviewerFromPullRequest)
            .withOperationName("remove reviewer")
      }
    }
  }

  object Gists : Entity("/gists") {
    @JvmStatic
    fun create(
      server: GithubServerPath,
      contents: List<GithubGistRequest.FileContent>, description: String, public: Boolean,
    ) =
      Post.json<GithubGist>(getUrl(server, urlSuffix),
                            GithubGistRequest(contents, description, public))
        .withOperation(GithubApiRequestOperation.RestCreateGist)
        .withOperationName("create gist")

    @JvmStatic
    fun get(server: GithubServerPath, id: String) = Get.Optional.json<GithubGist>(getUrl(server, urlSuffix, "/$id"))
      .withOperation(GithubApiRequestOperation.RestGetGist)
      .withOperationName("get gist $id")

    @JvmStatic
    fun delete(server: GithubServerPath, id: String) = Delete.json<Unit>(getUrl(server, urlSuffix, "/$id"))
      .withOperation(GithubApiRequestOperation.RestDeleteGist)
      .withOperationName("delete gist $id")
  }

  object Search : Entity("/search") {
    object Issues : Entity("/issues") {
      @JvmStatic
      fun pages(server: GithubServerPath, repoPath: GHRepositoryPath?, state: String?, assignee: String?, query: String?) =
        GithubApiPagesLoader.Request(get(server, repoPath, state, assignee, query), ::get)

      @JvmStatic
      fun get(
        server: GithubServerPath, repoPath: GHRepositoryPath?, state: String?, assignee: String?, query: String?,
        pagination: GithubRequestPagination? = null,
      ) =
        get(getUrl(server, urlSuffix, urlSuffix,
                   urlQuery {
                     param("q", searchQuery {
                       term(QualifierName.repo.createTerm(repoPath?.toString().orEmpty()))
                       term(QualifierName.state.createTerm(state.orEmpty()))
                       term(QualifierName.assignee.createTerm(assignee.orEmpty()))
                       query(query)
                     })
                     param("advanced_search", "true")
                     param(pagination)
                   }))

      @JvmStatic
      fun get(url: String) = Get.jsonSearchPage<GithubSearchedIssue>(url)
        .withOperation(GithubApiRequestOperation.RestSearchIssues)
        .withOperationName("search issues in repository")
    }
  }

  object Emojis : Entity("/emojis") {
    fun loadNameToUrlMap(server: GithubServerPath): GithubApiRequest<Map<String, String>> =
      Get.JsonMap<String, String>(getUrl(server, urlSuffix))
        .withOperation(GithubApiRequestOperation.RestGetEmojiMap)
        .withOperationName("load emoji name-to-url map")

    fun loadImage(url: String): GithubApiRequest<BufferedImage> =
      object : Get<BufferedImage>(url) {
        override fun extractResult(response: GithubApiResponse) =
          response.handleBody(ThrowableConvertor(GithubApiContentHelper::loadImage))
      }
        .withOperation(GithubApiRequestOperation.RestGetEmojiImage)
        .withOperationName("load emoji image")
  }

  abstract class Entity(val urlSuffix: String)

  private fun getUrl(server: GithubServerPath, suffix: String) = server.toApiUrl() + suffix

  private fun getUrl(repository: GHRepositoryCoordinates, vararg suffixes: String) =
    getUrl(repository.serverPath, Repos.urlSuffix, "/", repository.repositoryPath.toString(), *suffixes)

  fun getUrl(server: GithubServerPath, vararg suffixes: String) = StringBuilder(server.toApiUrl()).append(*suffixes).toString()
}