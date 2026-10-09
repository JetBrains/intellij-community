// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.codeInsight.AttachSourcesProvider.AttachSourcesAction
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import org.junit.Test
import org.junit.runners.Parameterized
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Tests the attach-sources action of [IntelliJPlatformAttachSourcesProvider] for a class from a `localIde` jar,
 * when the Gradle sync did not download the sources.
 *
 * A class from a jar with the Maven metadata of a bundled third-party library gets the sources of that library.
 * An IntelliJ Platform class from a jar that merges a library gets the IntelliJ Platform sources.
 *
 * The action runs on the IDE side, so the test runs against one Gradle version.
 */
class IntelliJPlatformAttachSourcesActionTest : IntelliJPlatformSourcesImportingTestCase() {

  companion object {
    private const val LIBRARY_ACTION_NAME = "Download library sources"
    private const val LIBRARY_FAILED_TITLE = "Library sources not found"

    private const val LIBRARY_JAR = "intellij.libraries.commons.cli.jar"
    private const val LIBRARY_CLASS = "org/apache/commons/cli/Option"

    private const val PLATFORM_JAR = "intellij.platform.ide.impl.jar"
    private const val PLATFORM_CLASS = "com/intellij/ide/IdeEventQueue"

    private val TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(3)

    @Parameterized.Parameters(name = "with Gradle-{0}")
    @JvmStatic
    fun tests(): Collection<Array<out String>> = listOf(arrayOf(BASE_GRADLE_VERSION))
  }

  override fun assumeGradleVersion() {}

  @Test
  fun `test library sources are attached to a class from a library jar`() {
    createLocalIde("intellij.libraries.commons.cli" to listOf(COMMONS_CLI_METADATA, classFile(LIBRARY_CLASS)), "app" to emptyList())
    createArtifact("com.jetbrains.intellij.idea", "ideaIC", "-sources")
    createArtifact(COMMONS_CLI_GROUP, "commons-cli", "-sources", sourceFile(LIBRARY_CLASS), version = COMMONS_CLI_VERSION)
    importProjectWithDependencies(LOCAL_IDE, downloadSources = false)
    val notifications = collectNotifications()

    val action = attachSources(LIBRARY_JAR, LIBRARY_CLASS)

    assertEquals(LIBRARY_ACTION_NAME, action.name)
    val sourceUrls = getSourceUrls(LOCAL_IDE)
    assertTrue("The library must get '$COMMONS_CLI_SOURCES_JAR': $sourceUrls", sourceUrls.any { it.endsWith("$COMMONS_CLI_SOURCES_JAR!/") })
    assertTrue("The library must not get '$SOURCES_JAR': $sourceUrls", sourceUrls.none { it.contains(SOURCES_JAR) })
    assertEmpty(notifications)
  }

  @Test
  fun `test platform sources are attached to a platform class from a merged jar`() {
    createLocalIde("intellij.platform.ide.impl" to listOf(SLF4J_METADATA, classFile(PLATFORM_CLASS)), "app" to emptyList())
    createArtifact("com.jetbrains.intellij.idea", "ideaIC", "-sources")
    createArtifact(SLF4J_GROUP, "slf4j-api", "-sources", version = SLF4J_VERSION)
    importProjectWithDependencies(LOCAL_IDE, downloadSources = false)
    val notifications = collectNotifications()

    val action = attachSources(PLATFORM_JAR, PLATFORM_CLASS)

    assertEquals(LIBRARY_ACTION_NAME, action.name)
    val sourceUrls = getSourceUrls(LOCAL_IDE)
    assertTrue("The library must get '$SOURCES_JAR': $sourceUrls", sourceUrls.any { it.endsWith("$SOURCES_JAR!/") })
    assertTrue("The library must not get '$SLF4J_SOURCES_JAR': $sourceUrls", sourceUrls.none { it.contains(SLF4J_SOURCES_JAR) })
    // The downloaded archive does not contain the package of the class. This is not a failure.
    assertEmpty(notifications)
  }

  @Test
  fun `test missing library sources are reported and the platform sources are attached`() {
    // The library does not exist in any repository. A real library, such as commons-cli, can come from a Gradle cache or a mirror of the build agent.
    val missingLibraryMetadata = pomProperties("org.jetbrains.test.missing", "missing-library", "1.0")
    createLocalIde("intellij.libraries.commons.cli" to listOf(missingLibraryMetadata, classFile(LIBRARY_CLASS)), "app" to emptyList())
    createArtifact("com.jetbrains.intellij.idea", "ideaIC", "-sources")
    importProjectWithDependencies(LOCAL_IDE, downloadSources = false)
    val notifications = collectNotifications()

    attachSources(LIBRARY_JAR, LIBRARY_CLASS)

    val sourceUrls = getSourceUrls(LOCAL_IDE)
    assertTrue("The library must get '$SOURCES_JAR': $sourceUrls", sourceUrls.any { it.endsWith("$SOURCES_JAR!/") })
    assertEquals(listOf(LIBRARY_FAILED_TITLE), notifications.map { it.title })
  }

  /**
   * Runs the attach-sources action for the class [internalName] of the [jarName] jar of the `localIde` library and waits for it.
   *
   * @return The action that was run.
   */
  private fun attachSources(jarName: String, internalName: String): AttachSourcesAction {
    val jarRoot = getLibrary(LOCAL_IDE).getFiles(OrderRootType.CLASSES).firstOrNull { it.name == jarName }
    assertNotNull("The jar '$jarName' must be a classes root of '$LOCAL_IDE'", jarRoot)
    val classFile = jarRoot!!.findFileByRelativePath("$internalName.class")
    assertNotNull("The class '$internalName' must exist in '$jarName'", classFile)

    val (action, libraries) = runReadActionBlocking {
      val psiFile = PsiManager.getInstance(myProject).findFile(classFile!!)
      assertNotNull("The class '$internalName' must have a PSI file", psiFile)
      val libraries: Collection<LibraryEntity> = ProjectFileIndex.getInstance(myProject).findContainingLibraries(classFile)
      val actions = IntelliJPlatformAttachSourcesProvider().getLibrariesActions(libraries, psiFile!!)
      assertSize(1, actions)
      actions.single()!! to libraries
    }

    val result = CompletableFuture<Boolean>()
    action.perform(libraries, myProject)
      .doWhenDone(Runnable { result.complete(true) })
      .doWhenRejected(Runnable { result.complete(false) })
    assertTrue("The action '${action.name}' must attach the sources", PlatformTestUtil.waitForFuture(result, TIMEOUT_MILLIS))
    return action
  }

  /**
   * Collects the notifications shown in the project from now on.
   */
  private fun collectNotifications(): List<Notification> {
    val notifications = CopyOnWriteArrayList<Notification>()
    myProject.messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        notifications += notification
      }
    })
    return notifications
  }
}
