// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import git4idea.commands.Git
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.remote.hosting.GitRemoteBranchesUtil.findOrCreateRemote
import git4idea.repo.GitRemote
import git4idea.repo.GitRepoInfo
import git4idea.repo.GitRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI

private const val ALIASED_URL = "git@host-alias:group/repo.git"

// The hosting API returns the canonical URLs, not the URLs with the SSH alias.
private const val API_HTTP_URL = "https://example.com/fork-owner/repo.git"
private const val API_SSH_URL = "git@example.com:fork-owner/repo.git"

class HostedGitRepositoryRemoteTest {

  @Test
  fun `SSH remote gives the root server URI with the alias host`() {
    val descriptor = createRemote(remoteCoordinates(gitRemote(ALIASED_URL)))

    assertThat(descriptor.name).isEqualTo("fork-owner")
    assertThat(descriptor.path).isEqualTo("fork-owner/repo")
    assertThat(descriptor.serverUri).isEqualTo(URI("https://host-alias/"))
  }

  @Test
  fun `SSH alias replaces the host of the SSH URL and keeps the HTTP URL`() {
    val descriptor = createRemote(remoteCoordinates(gitRemote(ALIASED_URL)))

    assertThat(descriptor.httpUrl).isEqualTo(API_HTTP_URL)
    assertThat(descriptor.sshUrl).isEqualTo("git@host-alias:fork-owner/repo.git")
  }

  @Test
  fun `SSH alias drops the project port when the remote has no explicit port`() {
    val coordinates = remoteCoordinates(gitRemote(ALIASED_URL))

    val descriptor = createRemote(coordinates, sshUrl = "ssh://git@example.com:2222/fork-owner/repo.git")

    assertThat(descriptor.sshUrl).isEqualTo("git@host-alias:fork-owner/repo.git")
  }

  @Test
  fun `SSH alias keeps the explicit remote port for an SSH project URL`() {
    val coordinates = remoteCoordinates(gitRemote("ssh://git@host-alias:2222/group/repo.git"))

    val descriptor = createRemote(coordinates, sshUrl = "ssh://git@example.com:22/fork-owner/repo.git")

    assertThat(descriptor.sshUrl).isEqualTo("ssh://git@host-alias:2222/fork-owner/repo.git")
  }

  @Test
  fun `SSH alias keeps the explicit remote port for an SCP project URL`() {
    val coordinates = remoteCoordinates(gitRemote("ssh://git@host-alias:2222/group/repo.git"))

    val descriptor = createRemote(coordinates)

    assertThat(descriptor.sshUrl).isEqualTo("ssh://git@host-alias:2222/fork-owner/repo.git")
  }

  @Test
  fun `SSH URL keeps the project port with the same host`() {
    val coordinates = remoteCoordinates(gitRemote("git@example.com:group/repo.git"))

    val descriptor = createRemote(coordinates, sshUrl = "ssh://git@example.com:2222/fork-owner/repo.git")

    assertThat(descriptor.sshUrl).isEqualTo("ssh://git@example.com:2222/fork-owner/repo.git")
  }

  @Test
  fun `SSH URL keeps the explicit remote port with the same host`() {
    val coordinates = remoteCoordinates(gitRemote("ssh://git@example.com:2222/group/repo.git"))

    val descriptor = createRemote(coordinates)

    assertThat(descriptor.sshUrl).isEqualTo("ssh://git@example.com:2222/fork-owner/repo.git")
  }

  @Test
  fun `invalid SSH URL stays unchanged`() {
    val coordinates = remoteCoordinates(gitRemote(ALIASED_URL))

    val descriptor = createRemote(coordinates, sshUrl = "ssh://git@bad host/fork-owner/repo.git")

    assertThat(descriptor.sshUrl).isEqualTo("ssh://git@bad host/fork-owner/repo.git")
  }

  @Test
  fun `descriptor has no SSH URL when the API has none`() {
    val descriptor = createRemote(remoteCoordinates(gitRemote(ALIASED_URL)), sshUrl = null)

    assertThat(descriptor.sshUrl).isNull()
  }

  @Test
  fun `HTTP remote keeps the web path from the project HTTP URL`() {
    val coordinates = remoteCoordinates(gitRemote("https://example.com/web/group/repo.git"))

    val descriptor = createRemote(coordinates, httpUrl = "https://example.com/web/fork-owner/repo.git")

    assertThat(descriptor.serverUri).isEqualTo(URI("https://example.com/web/"))
  }

  @Test
  fun `HTTP remote gives the root server URI when the project HTTP URL has no web path`() {
    val coordinates = remoteCoordinates(gitRemote("https://example.com/web/group/repo.git"))

    assertThat(createRemote(coordinates).serverUri).isEqualTo(URI("https://example.com/"))
    assertThat(createRemote(coordinates, httpUrl = null).serverUri).isEqualTo(URI("https://example.com/"))
  }

  @Test
  fun `HTTP remote keeps the SSH URL from the API`() {
    val coordinates = remoteCoordinates(gitRemote("https://host-alias/group/repo.git"))

    assertThat(createRemote(coordinates).sshUrl).isEqualTo(API_SSH_URL)
  }

  @Test
  fun `matching prefers the SSH alias over the canonical host`() {
    val origin = gitRemote(ALIASED_URL)
    val canonicalFork = gitRemote(API_SSH_URL, "canonical")
    val aliasedFork = gitRemote("git@host-alias:fork-owner/repo.git", "fork")
    val coordinates = remoteCoordinates(origin)
    every { coordinates.repository.info.remotes } returns listOf(origin, canonicalFork, aliasedFork)

    val descriptor = createRemote(coordinates)

    assertThat(GitRemoteBranchesUtil.findRemote(coordinates.repository, descriptor)).isEqualTo(aliasedFork)
  }

  @Test
  fun `HTTP matching keeps the server web path`() {
    val origin = gitRemote("https://example.com/web/group/repo.git")
    val otherFork = gitRemote("https://example.com/other/fork-owner/repo.git", "other-fork")
    val fork = gitRemote("https://example.com/web/fork-owner/repo.git", "fork")
    val coordinates = remoteCoordinates(origin)
    every { coordinates.repository.info.remotes } returns listOf(origin, otherFork, fork)

    val descriptor = createRemote(coordinates, httpUrl = "https://example.com/web/fork-owner/repo.git")

    assertThat(GitRemoteBranchesUtil.findRemote(coordinates.repository, descriptor)).isEqualTo(fork)
  }

  @Test
  fun `creating an HTTP fork remote uses the HTTP URL from the API`() {
    val origin = gitRemote("http://example.com:8080/group/repo.git")
    val coordinates = remoteCoordinates(origin)
    val repository = coordinates.repository
    val remotes = mutableListOf(origin)
    every { repository.remotes } returns remotes
    every { repository.update() } returns Unit
    val descriptor = createRemote(coordinates)
    val addedUrl = slot<String>()
    val git = mockk<Git> {
      every { addRemote(repository, "fork-owner", capture(addedUrl)) } answers {
        remotes.add(gitRemote(addedUrl.captured, "fork-owner"))
        mockk()
      }
    }

    val createdRemote = git.findOrCreateRemote(repository, descriptor)

    verify(exactly = 1) { git.addRemote(repository, "fork-owner", API_HTTP_URL) }
    assertThat(createdRemote?.firstUrl).isEqualTo(API_HTTP_URL)
  }

  private fun createRemote(
    coordinates: GitRemoteUrlCoordinates,
    httpUrl: String? = API_HTTP_URL,
    sshUrl: String? = API_SSH_URL,
  ): HostedGitRepositoryRemote =
    createHostedGitRepositoryRemote("fork-owner", "fork-owner/repo", httpUrl, sshUrl, coordinates)

  private fun gitRemote(url: String, name: String = "origin"): GitRemote =
    GitRemote(name = name, urls = listOf(url), pushUrls = listOf(url), fetchRefSpecs = listOf(), pushRefSpecs = listOf())

  private fun remoteCoordinates(remote: GitRemote): GitRemoteUrlCoordinates {
    val repositoryInfo = mockk<GitRepoInfo> { every { remotes } returns listOf(remote) }
    val repository = mockk<GitRepository> { every { info } returns repositoryInfo }
    return GitRemoteUrlCoordinates(remote.firstUrl!!, remote, repository)
  }
}
