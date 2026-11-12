// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vcs.VcsException
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.EelUnavailableException
import com.intellij.platform.eel.provider.EelMachineResolver
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.registerExtension
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * An Eel environment can go away at any time, for example when the SSH connection to a remote host drops.
 * Git must report it as a failed command, not throw from the handler constructor (the platform logs that as an error).
 */
@TestApplication
internal class GitEelUnavailableEnvironmentTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @TempDir
  private lateinit var tempDir: Path

  @Test
  fun `a lost environment gives a failed Git command`() {
    val environment = TestEnvironment(disposable)

    val executable = GitEelExecutableDetectionHelper.createEelExecutable(environment.descriptor, "git")
    assertInstanceOf(GitExecutable.Unknown::class.java, executable)

    for (preValidate in listOf(true, false)) {
      val handler = GitLineHandler(null, tempDir, executable, GitCommand.VERSION, emptyList())
      handler.setPreValidateExecutable(preValidate)
      val result = Git.getInstance().runCommand(handler)
      assertFalse(result.success(), "preValidate=$preValidate: $result")
      assertThrows<VcsException>("preValidate=$preValidate") { result.getOutputOrThrow() }
    }
  }

  @Test
  fun `an environment that is back gives an Eel executable`() {
    val environment = TestEnvironment(disposable)
    environment.available = true

    val executable = GitEelExecutableDetectionHelper.createEelExecutable(environment.descriptor, "git")
    assertInstanceOf(GitExecutable.Eel::class.java, executable)
  }

  @Test
  fun `a lost connection is retried by the next blocking detection`() {
    val environment = TestEnvironment(disposable)
    val helper = GitEelExecutableDetectionHelper.getInstance()
    val rootDir = tempDir.toString()

    assertNull(helper.getExecutablePathBlocking(environment.descriptor, rootDir))
    assertEquals(1, environment.connectionAttempts.get())

    environment.available = true
    assertNotNull(helper.getExecutablePathBlocking(environment.descriptor, rootDir), "Git must be found once the environment is back")
    assertEquals(2, environment.connectionAttempts.get())
  }

  @Test
  fun `a peek does not restart a failed detection`() {
    val environment = TestEnvironment(disposable)
    val helper = GitEelExecutableDetectionHelper.getInstance()
    val rootDir = tempDir.toString()

    assertNull(helper.getExecutablePathBlocking(environment.descriptor, rootDir))
    environment.available = true
    repeat(3) {
      assertNull(helper.getExecutablePathIfReady(environment.descriptor, rootDir))
    }
    assertEquals(1, environment.connectionAttempts.get(), "A peek must not connect again")
  }

  @Test
  fun `a peek starts the first detection`() {
    val environment = TestEnvironment(disposable)
    environment.available = true
    val helper = GitEelExecutableDetectionHelper.getInstance()
    val rootDir = tempDir.toString()

    helper.getExecutablePathIfReady(environment.descriptor, rootDir)
    timeoutRunBlocking { helper.getExecutablePathPromise(environment.descriptor, rootDir).await() }
    assertNotNull(helper.getExecutablePathIfReady(environment.descriptor, rootDir))
    assertEquals(1, environment.connectionAttempts.get())
  }

  @Test
  fun `an intended exit is not retried`() {
    val environment = TestEnvironment(disposable) { EelUnavailableException.IntendedExit("The user cancelled the authentication", null) }
    val helper = GitEelExecutableDetectionHelper.getInstance()
    val rootDir = tempDir.toString()

    assertNull(helper.getExecutablePathBlocking(environment.descriptor, rootDir))
    environment.available = true
    assertNull(helper.getExecutablePathBlocking(environment.descriptor, rootDir))
    assertNull(helper.getExecutablePathIfReady(environment.descriptor, rootDir))
    assertEquals(1, environment.connectionAttempts.get(), "An intended exit must not connect again")
  }

  /**
   * A fake remote environment: while not [available], each connection fails with [failure], by default like a lost SSH connection.
   * When available, it connects to the local machine.
   */
  private class TestEnvironment(
    disposable: Disposable,
    private val failure: () -> EelUnavailableException = { EelUnavailableException.CommunicationFailure("The test environment is unreachable") },
  ) {
    @Volatile
    var available: Boolean = false

    val connectionAttempts: AtomicInteger = AtomicInteger()

    private val id = UUID.randomUUID().toString()

    val descriptor: EelDescriptor = object : EelDescriptor {
      override val name: String = "git-test-environment-$id"
      override val osFamily = LocalEelDescriptor.osFamily
    }

    private val machine: EelMachine = object : EelMachine {
      override val isConnected: Boolean get() = available
      override val internalName: String = "git-test-machine-$id"

      override suspend fun toEelApi(descriptor: EelDescriptor): EelApi {
        connectionAttempts.incrementAndGet()
        if (!available) throw failure()
        return localEel
      }

      override fun ownsDescriptor(descriptor: EelDescriptor): Boolean = descriptor === this@TestEnvironment.descriptor
    }

    init {
      ApplicationManager.getApplication().registerExtension(EEL_MACHINE_RESOLVER_EP, object : EelMachineResolver {
        override fun getResolvedEelMachine(eelDescriptor: EelDescriptor): EelMachine? = machine.takeIf { eelDescriptor === descriptor }
        override suspend fun resolveEelMachine(eelDescriptor: EelDescriptor): EelMachine? = getResolvedEelMachine(eelDescriptor)
        override suspend fun resolveEelMachineByInternalName(internalName: String): EelMachine? =
          machine.takeIf { internalName == machine.internalName }
      }, disposable)
    }
  }

  private companion object {
    // The extension point lives in eel-impl, which this module does not depend on.
    val EEL_MACHINE_RESOLVER_EP = ExtensionPointName<EelMachineResolver>("com.intellij.eelMachineResolver")
  }
}
