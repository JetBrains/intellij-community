// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.apitests

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.ThreadLeakTracker
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.gitlab.GitLabServersManager
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabEdition
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.GitLabServerMetadata
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.GitLabVersion
import org.jetbrains.plugins.gitlab.api.dto.GitLabGraphQLMutationResultDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabLabelDTO
import org.jetbrains.plugins.gitlab.apitests.GitLabApiTestCase.Companion.IDEA_TEST_GITLAB_API_DATA_PATH
import org.jetbrains.plugins.gitlab.apitests.GitLabApiTestCase.Companion.IDEA_TEST_GITLAB_API_EDITION
import org.jetbrains.plugins.gitlab.apitests.GitLabApiTestCase.Companion.IDEA_TEST_GITLAB_API_TOKEN
import org.jetbrains.plugins.gitlab.apitests.GitLabApiTestCase.Companion.IDEA_TEST_GITLAB_API_VERSION
import org.jetbrains.plugins.gitlab.apitests.GitLabApiTestCase.Companion.IDEA_TEST_GITLAB_URI
import org.jetbrains.plugins.gitlab.util.GitLabProjectPath
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.assertNotNull
import org.opentest4j.TestAbortedException
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.isDirectory
import kotlin.io.path.writeText

typealias VersionPredicate = (GitLabVersion) -> Boolean
typealias MetadataPredicate = (GitLabServerMetadata) -> Boolean

/**
 * To run the tests, you need to either have a valid GitLab server running locally (the only viable option for Win),
 * or the test will run the new Gitlab docker container for each test class and shut it down after the test.

 * For both options set the next environment variables:
 * - [IDEA_TEST_GITLAB_API_VERSION] - version number, e.g. "14.0.0"
 * - [IDEA_TEST_GITLAB_API_EDITION] - version edition: "ce" or "ee"
 * - [IDEA_TEST_GITLAB_API_TOKEN] - API token for the access to the GitLab server for "root" user
 * For reusing the already running Gitlab server:
 * - [IDEA_TEST_GITLAB_URI] environment variable should contain the Gitlab server URI,
 * For automatically running a new container for each test:
 * - [IDEA_TEST_GITLAB_API_DATA_PATH] environment variable should contain the path to the server backup data
 *
 * See https://git.jetbrains.team/vcs-collab-tools/gitlab-docker.git for more details on GitLab Docker setup and credentials.
 *
 * The server backup data for an old version is available in https://jetbrains.team/p/vcs-collab-tools/packages/files/gitlab-server-data
 */
@TestApplication
abstract class GitLabApiTestCase {
  class GitLabDataConstants {
    val rootUsername = "root"

    val testsGroupLabel1 = GitLabLabelDTO("grouplabel1")
    val testsGroupLabel2 = GitLabLabelDTO("grouplabel2")

    val glTests2Project = GitLabProjectPath("tests", "gl-tests-2")
    val glTests2Coordinates = GitLabProjectCoordinates(server, glTests2Project)
    val glTests2Label1 = GitLabLabelDTO("label1")
    val glTests2Label2 = GitLabLabelDTO("label2")

    val glTest1Project = GitLabProjectPath("root", "gl-test-1")
    val glTest1Coordinates = GitLabProjectCoordinates(server, glTest1Project)

    val glTest1Mr2Iid = "2"
    val glTest1Mr2CommitShas = listOf("6a7d70b5df2d77ad792bca08c6fb14e29ae4ad04")
    val glTest1Mr2CommitShortShas = listOf("6a7d70b5")
    val glTest1Mr2ChangedFiles = listOf("a/important.txt")

    val volatileProject = GitLabProjectPath("volatile", "volatile-project")
    val volatileProjectCoordinates = GitLabProjectCoordinates(server, volatileProject)
    val volatileProjectMr1Iid = "1"
    val volatileProjectMr1Gid = "gid://gitlab/MergeRequest/5"
    val volatileProjectMr2Iid = "2"
  }

  companion object {
    private const val IDEA_TEST_GITLAB_URI = "idea_test_gitlab_uri"
    private const val IDEA_TEST_GITLAB_API_DATA_PATH = "idea_test_gitlab_api_data_path"
    private const val IDEA_TEST_GITLAB_API_VERSION = "idea_test_gitlab_api_version"
    private const val IDEA_TEST_GITLAB_API_TOKEN = "idea_test_gitlab_api_token"
    private const val IDEA_TEST_GITLAB_API_EDITION = "idea_test_gitlab_api_edition"

    private var classRootDisposable: Disposable? = null
    private var container: DockerComposeContainer<*>? = null

    private val constants: GitLabDataConstants by lazy { GitLabDataConstants() }

    private var _server: GitLabServerPath? = null
    protected val server: GitLabServerPath
      get() = _server ?: throwExplanation()

    private var _token: String? = null
    protected val token: String
      get() = _token ?: throwExplanation()

    private var _version: GitLabVersion? = null
    val version: GitLabVersion
      get() = _version ?: throwExplanation()

    private var _edition: GitLabEdition? = null
    val edition: GitLabEdition
      get() = _edition ?: throwExplanation()

    private var _unauthenticatedApi: GitLabApi? = null
    private val unauthenticatedApi: GitLabApi
      get() = _unauthenticatedApi ?: throwExplanation()

    private var _authenticatedApi: GitLabApi? = null
    private val authenticatedApi: GitLabApi
      get() = _authenticatedApi ?: throwExplanation()

    protected val metadata: GitLabServerMetadata
      get() = GitLabServerMetadata(version, "-", edition)

    private val foundEnvironmentVariables: String
      get() = """
      - $IDEA_TEST_GITLAB_API_DATA_PATH=${System.getenv(IDEA_TEST_GITLAB_API_DATA_PATH)}
      - $IDEA_TEST_GITLAB_API_TOKEN=${System.getenv(IDEA_TEST_GITLAB_API_TOKEN)?.replace(".".toRegex(), "*")}
      - $IDEA_TEST_GITLAB_API_VERSION=${System.getenv(IDEA_TEST_GITLAB_API_VERSION)}
      - $IDEA_TEST_GITLAB_API_EDITION=${System.getenv(IDEA_TEST_GITLAB_API_EDITION)}
      - (for local run only) $IDEA_TEST_GITLAB_URI=${System.getenv(IDEA_TEST_GITLAB_URI)} 
    """

    private val serversManager by lazy {
      object : GitLabServersManager {
        override val earliestSupportedVersion: GitLabVersion = GitLabVersion(9, 0)

        override suspend fun checkIsGitLabServer(server: GitLabServerPath): Boolean =
          server == Companion.server

        override suspend fun getMetadata(api: GitLabApi): GitLabServerMetadata =
          GitLabServerMetadata(version, "-", edition)
      }
    }

    private val apiManager by lazy {
      object : GitLabApiManager() {
        override val serversManager: GitLabServersManager = this@Companion.serversManager
      }
    }

    /**
     * Checks that the edition is exactly the given edition.
     */
    fun checkMetadata(metadataPredicate: MetadataPredicate) {
      Assumptions.assumeTrue(metadataPredicate(metadata), "Skipped on metadata: $metadata")
    }

    /**
     * Checks the version of the currently used API and aborts the test if the version should
     * not be tested. This means the test will not be counted towards pass/fail, it will simply
     * be ignored.
     *
     * Used at the start of an API test method.
     */
    fun checkVersion(versionPredicate: VersionPredicate) {
      Assumptions.assumeTrue(versionPredicate(version), "Version not in range: $version")
    }

    /**
     * A convenience function to short-hand creating [GitLabVersion].
     */
    fun v(major: Int, minor: Int? = null, patch: Int? = null) =
      GitLabVersion(major, minor, patch)

    /**
     * Asserts that a mutation caused no errors.
     */
    fun <V> GitLabGraphQLMutationResultDTO<V>?.assertNoErrors() {
      assertNotNull(this)
      this.errors?.let { assertTrue(it.isEmpty()) }
      assertNotNull(this.value)
    }

    /**
     * Tests some condition in the given block both with the unauthenticated and authenticated API.
     */
    suspend fun <T> requiresNoAuthentication(test: suspend GitLabDataConstants.(GitLabApi) -> T) {
      val e1 = runCatching { constants.test(unauthenticatedApi) }.exceptionOrNull()
      val e2 = runCatching { constants.test(authenticatedApi) }.exceptionOrNull()

      if (e1 == null && e2 == null) return
      if (e1 == null || e2 == null) throw (e1 ?: e2)!!
      if (e1 is TestAbortedException && e2 is TestAbortedException) {
        throw TestAbortedException("Unauthenticated:\n${e1.message}\n\nAuthenticated:\n${e2.message}")
      }

      throw CompositeException(listOf(e1, e2))
    }

    /**
     * Tests some condition in the given block only for authenticated API.
     * It makes no sense to check that unauthenticated API can indeed not be used, because we are
     * already restrictive in usage of the API if this test is called anyway.
     */
    suspend fun <T> requiresAuthentication(test: suspend GitLabDataConstants.(GitLabApi) -> T) {
      constants.test(authenticatedApi)
    }

    @BeforeAll
    @JvmStatic
    fun setUpServer() {
      println("""
        Setting up API tests with environment variables:
        $foundEnvironmentVariables
      """.trimIndent())

      _token = System.getenv(IDEA_TEST_GITLAB_API_TOKEN)
      _version = GitLabVersion.fromString(System.getenv(IDEA_TEST_GITLAB_API_VERSION) ?: throwExplanation())
      _edition = when (System.getenv(IDEA_TEST_GITLAB_API_EDITION)) {
        "ce" -> GitLabEdition.Community
        "ee" -> GitLabEdition.Enterprise
        else -> null
      }

      _server = createProjectPath()
      classRootDisposable = Disposer.newCheckedDisposable()
      ThreadLeakTracker.longRunningThreadCreated(classRootDisposable!!, "ryuk", "testcontainers", "testcontainers-ryuk")

      _unauthenticatedApi = apiManager.getUnauthenticatedClient(server)
      _authenticatedApi = apiManager.getClient(server, token)
    }

    @AfterAll
    @JvmStatic
    fun tearDownServer() {
      container?.stop()
      classRootDisposable?.let(Disposer::dispose)
    }

    /**
     * Creates a GitLab server path from the environment variables or run a new container and use its path
     */
    private fun createProjectPath() : GitLabServerPath{
      System.getenv(IDEA_TEST_GITLAB_URI)?.let {
        return GitLabServerPath(it)
      }

      val newContainer = startContainer()
      container = newContainer
      return GitLabServerPath("http://" +
                                 newContainer.getServiceHost("gitlab-server", 80) +
                                 ":" +
                                 newContainer.getServicePort("gitlab-server", 80))
    }

    /**
     * Checks that the container for GitLab server has spun up and sets the server field.
     */
    private fun startContainer(): DockerComposeContainer<*> {
      val dataPath = System.getenv(IDEA_TEST_GITLAB_API_DATA_PATH) ?: throwExplanation()
      checkDataPath(dataPath)

      val edition = when (metadata.edition) {
        GitLabEdition.Community -> "ce"
        GitLabEdition.Enterprise -> "ee"
      }
      val version = metadata.version.toString()

      // Not ideal, env variables are only passed to the compose container, not nested containers:
      // https://stackoverflow.com/questions/69767291/testcontainers-dockercomposecontainer-withenv
      val composeFile = Files.createTempFile("compose", ".yml")
      composeFile.writeText(
        String(Companion::class.java.classLoader.getResourceAsStream("compose.yml")?.readAllBytes() ?: throwExplanation())
          .replace("\${EDITION}", edition)
          .replace("\${VERSION}", version)
          .replace("\${DATA_PATH}", dataPath))

      // Wait for an API endpoint to ensure API is available
      val container = DockerComposeContainer(listOf(composeFile.toFile()))
        .withExposedService("gitlab-server", 80,
                            Wait.forHttp("/api/v4/projects")
                              .forStatusCode(200)
                              .withStartupTimeout(Duration.ofMinutes(5)))

      container.start()
      Thread.sleep(5000) // Sleep just a slight bit longer to allow GitLab time to start up

      return container
    }

    private fun checkDataPath(dataPath: String) {
      val path = Path.of(dataPath)
      if (path.isDirectory() && path.resolve("config").isDirectory() && path.resolve("data").isDirectory()) {
        return
      }

      fail<Unit>("""
        Invalid data path: '${dataPath}', expected a config and a data directory to be present under this directory.
      """.trimIndent())
    }

    private fun <T> throwExplanation(): T {
      fail<Unit>("""
        Check for more documentation: ${GitLabApiTestCase::class.java}
        
        Sadly, something went wrong running these tests.
        
        For GitLab API tests (these), some special setup is required.
        Exclude these tests in local tests if you do not have a GitLab server
        specifically setup for these tests.
        
        If you did intend to run these tests, consider the following configurations.
        
        Did you configure the following environment variables:
        1. '$IDEA_TEST_GITLAB_API_DATA_PATH' containing the path to the data for the GL server.
        2. '$IDEA_TEST_GITLAB_API_VERSION' containing the version of the GL server to test against.
        3. '$IDEA_TEST_GITLAB_API_EDITION' containing the edition of the GL server to test against.
        4. '$IDEA_TEST_GITLAB_API_TOKEN' containing the token that can be used to authenticate to
           the GL API.
           
        (optionally, for LOCAL run you can use '$IDEA_TEST_GITLAB_URI' instead of '$IDEA_TEST_GITLAB_API_DATA_PATH' 
        to specify the URI of the existing GL server)
           
        Found:
        $foundEnvironmentVariables
          
      """.trimIndent())

      throw RuntimeException()
    }

    fun after(version: GitLabVersion): VersionPredicate =
      { version <= it }

    fun until(version: GitLabVersion): VersionPredicate =
      { it < version }

    fun inRange(start: GitLabVersion, end: GitLabVersion): VersionPredicate =
      { start <= it && it < end }
  }

  class CompositeException(
    exceptions: List<Throwable>
  ) : RuntimeException(exceptions.joinToString("\n\n") { it.stackTraceToString() })
}