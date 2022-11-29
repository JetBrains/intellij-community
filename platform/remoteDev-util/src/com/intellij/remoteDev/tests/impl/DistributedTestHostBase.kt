package com.intellij.remoteDev.tests.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.isLocal
import com.intellij.diagnostic.DebugLogManager
import com.intellij.diagnostic.ThreadDumper
import com.intellij.ide.IdeEventQueue
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.WindowManager
import com.intellij.remoteDev.tests.*
import com.intellij.remoteDev.tests.modelGenerated.*
import com.intellij.util.alsoIfNull
import com.intellij.util.application
import com.intellij.util.ui.ImageUtil
import com.jetbrains.rd.framework.*
import com.jetbrains.rd.framework.impl.RdTask
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.measureTimeMillis
import com.jetbrains.rd.util.reactive.viewNotNull
import org.jetbrains.annotations.ApiStatus
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.imageio.ImageIO
import kotlin.reflect.full.createInstance


class DistributedTestHost(private val agentProject: Project) : DistributedTestHostBase() {
  override val projectOrNull: Project
    get() = agentProject
  override val project: Project
    get() = agentProject

}

class DistributedTestHostGateway : DistributedTestHostBase() {
  override val projectOrNull: Project?
    get() = null
  override val project: Project
    get() = error("Project shouldn't be referenced for gateway")

}

@ApiStatus.Internal
abstract class DistributedTestHostBase() {

  companion object {
    private val logger = Logger.getInstance(DistributedTestHostBase::class.java)

    const val screenshotOnFailureFileName = "ScreenshotOnFailure"
  }

  protected abstract val projectOrNull: Project?
  protected abstract val project: Project

  init {
    val hostAddress = when (SystemInfo.isLinux) {
      true -> System.getenv(AgentConstants.dockerHostIpEnvVar)?.let {
        logger.info("${AgentConstants.dockerHostIpEnvVar} env var is set=$it, will try to get address from it.")
        // this won't work when we do custom network setups as the default gateway will be overridden
        // val hostEntries = File("/etc/hosts").readText().lines()
        // val dockerInterfaceEntry = hostEntries.last { it.isNotBlank() }
        // val ipAddress = dockerInterfaceEntry.split("\\s".toRegex()).first()
        InetAddress.getByName(it)
      } ?: InetAddress.getLoopbackAddress()
      false -> InetAddress.getLoopbackAddress()
    }

    logger.info("Host address=${hostAddress}")

    val port = System.getenv(AgentConstants.protocolPortEnvVar)?.toIntOrNull()

    if (port != null) {
      logger.info("Queue creating protocol on port $port")
      application.invokeLater { createProtocol(hostAddress, port) }
    }
  }

  private fun createProtocol(hostAddress: InetAddress, port: Int) {
    logger.info("Creating protocol...")

    val lifetime = LifetimeDefinition()
    projectOrNull?.whenDisposed { lifetime.terminate() }
      .alsoIfNull { application.whenDisposed { lifetime.terminate() } }

    val wire = SocketWire.Client(lifetime, DistributedTestIdeScheduler, port, AgentConstants.protocolName, hostAddress)
    val protocol =
      Protocol(AgentConstants.protocolName, Serializers(), Identities(IdKind.Client), DistributedTestIdeScheduler, wire, lifetime)
    val model = protocol.distributedTestModel

    logger.info("Advise for session...")
    model.session.viewNotNull(lifetime) { lt, session ->
      try {
        val context = AgentContext(session.agentId, application, projectOrNull, protocol)
        logger.info("New test session: ${session.testClassName}.${session.testMethodName}")
        logger.info("Setting up loggers")
        AgentTestLoggerFactory.bindSession(lt, session)

        // Create test class
        val testClass = Class.forName(session.testClassName)
        val testClassObject = testClass.kotlin.createInstance() as DistributedTestPlayer

        // Tell test we are running it inside an agent
        val agentInfo = AgentInfo(session.agentId, session.testMethodName)
        val queue = testClassObject.initAgent(agentInfo)

        // Play test method
        val testMethod = testClass.getMethod(session.testMethodName)
        testClassObject.performInit(testMethod)
        testMethod.invoke(testClassObject)

        // Advice for processing events
        session.runNextAction.set { _, _ ->
          var actionTitle: String? = null
          try {
            assert(ClientId.current.isLocal) { "ClientId '${ClientId.current}' should be local when test method starts" }

            val action = queue.remove()
            actionTitle = action.title
            showNotification(actionTitle)

            // Flush all events to process pending protocol events and other things
            //   before actual test method execution
            IdeEventQueue.getInstance().flushQueue()

            // Execute test method
            lateinit var result: RdTask<Boolean>
            logger.info("'$actionTitle': starting action")
            val elapsedAction = measureTimeMillis {
              result = action.action.invoke(context)
            }
            logger.info("'$actionTitle': completed action in ${elapsedAction}ms")

            projectOrNull?.let {
              // Sync state across all IDE agents to maintain proper order in protocol events
              logger.info("Sync protocol events after execution...")
              val elapsedSync = measureTimeMillis {
                DistributedTestBridge.getInstance(it).syncProtocolEvents()
                IdeEventQueue.getInstance().flushQueue()
              }
              logger.info("Protocol state sync completed in ${elapsedSync}ms")
            }

            // Assert state
            assertStateAfterTestMethod()

            return@set result
          }
          catch (ex: Throwable) {
            logger.warn("Test action ${actionTitle?.let { "'$it' " }.orEmpty()}hasn't finished successfully", ex)
            if (!application.isHeadlessEnvironment)
              actionTitle?.let { makeScreenshot("${it}_$screenshotOnFailureFileName") }
            return@set RdTask.faulted(ex)
          }
        }

        session.shutdown.advise(lifetime) {
          projectOrNull?.let {
            logger.info("Close project...")
            try {
              ProjectManagerEx.getInstanceEx().forceCloseProject(it)
            }
            catch (e: Exception) {
              logger.error("Error on project closing", e)
            }
          }

          logger.info("Shutdown application...")
          application.exit(true, true, false)
        }

        session.dumpThreads.adviseOn(lifetime, DistributedTestInplaceScheduler) {
          logger.info("Dump threads...")
          val threadDump = ThreadDumper.dumpThreadsToString()
          val threadDumpStamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH_mm_ss_SSS"))
          val threadDumpFileName = "${AgentConstants.threadDumpFilePrefix}_$threadDumpStamp.log"
          val threadDumpFile = File(PathManager.getLogPath()).resolve(threadDumpFileName)
          threadDumpFile.writeText(threadDump)
        }

        session.makeScreenshot.set { fileName ->
          return@set makeScreenshot(fileName)
        }

        // Initialize loggers
        DebugLogManager.getInstance().applyCategories(
          session.traceCategories.map { DebugLogManager.Category(it, DebugLogManager.DebugLogLevel.TRACE) }
        )

        logger.info("Test session ready!")
        session.ready.value = true
      }
      catch (ex: Throwable) {
        logger.error("Test session initialization hasn't finished successfully", ex)
        session.ready.value = false
      }
    }
  }

  private fun makeScreenshot(fileName: String): Boolean {
    if (application.isHeadlessEnvironment) {
      error("Don't try making screenshots on application in headless mode.")
    }

    val result = CompletableFuture<Boolean>()
    ApplicationManager.getApplication().invokeLater {
      val frame = WindowManager.getInstance().getIdeFrame(project)
      if (frame != null) {
        val component = frame.component
        val img = ImageUtil.createImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
        component.printAll(img.createGraphics())
        ApplicationManager.getApplication().executeOnPooledThread {
          try {
            result.complete(ImageIO.write(img, "png", File(PathManager.getLogPath()).resolve(
              if (fileName.endsWith(".png")) fileName else "$fileName.png")))
          }
          catch (e: IOException) {
            logger.info(e)
          }
        }
      }
      else
        logger.info("Frame was empty when makeScreenshot was called")
    }

    IdeEventQueue.getInstance().flushQueue()

    try {
      if (result[45, TimeUnit.SECONDS])
        logger.info("Screenshot is saved at: $fileName")
      else
        logger.info("No writers were found for screenshot")
    }
    catch (e: Throwable) {
      when (e) {
        is InterruptedException, is ExecutionException, is TimeoutException -> logger.info(e)
        else -> {
          logger.warn("Test action 'makeScreenshot' hasn't finished successfully", e)
          return false
        }
      }
    }
    return true
  }

  private fun assertStateAfterTestMethod() {
    assert(Logger.getFactory() is AgentTestLoggerFactory) {
      "Logger Factory was overridden during test method execution. " +
      "Inspect logs to find stack trace of the overrider. " +
      "Overriding logger factory leads to breaking distributes test log processing."
    }
  }

  @Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")
  private fun showNotification(text: String?): Notification? {
    if (application.isHeadlessEnvironment || text.isNullOrBlank())
      return null

    val notification = Notification("TestFramework",
                                    "Test Framework",
                                    text,
                                    NotificationType.INFORMATION)
    Notifications.Bus.notify(notification)
    return notification
  }

  private fun Throwable.toModel(): RdTestSessionException {
    fun getRdTestStackTraceElement(trace: Array<StackTraceElement>?): List<RdTestSessionStackTraceElement> =
      trace?.map { it ->
        RdTestSessionStackTraceElement(it.className, it.methodName, it.fileName.orEmpty(), it.lineNumber)
      } ?: emptyList()

    val rdTestSessionExceptionCause = this.cause?.let { cause ->
      RdTestSessionExceptionCause(
        cause.javaClass.typeName,
        cause.message,
        getRdTestStackTraceElement(cause.stackTrace)
      )
    }

    return RdTestSessionException(
      this.javaClass.typeName,
      this.message,
      getRdTestStackTraceElement(this.stackTrace),
      rdTestSessionExceptionCause
    )
  }
}