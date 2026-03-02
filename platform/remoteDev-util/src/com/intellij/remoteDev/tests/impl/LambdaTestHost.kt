package com.intellij.remoteDev.tests.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.isLocal
import com.intellij.codeWithMe.ClientIdContextElement
import com.intellij.codeWithMe.clientId
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.dumpCoroutines
import com.intellij.diagnostic.enableCoroutineDump
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginModuleDescriptor
import com.intellij.ide.plugins.PluginModuleId
import com.intellij.idea.AppMode
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.client.ClientSessionsManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.rd.util.setSuspend
import com.intellij.remoteDev.tests.LambdaBackendContextClass
import com.intellij.remoteDev.tests.LambdaFrontendContextClass
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.LambdaIdeContextClass
import com.intellij.remoteDev.tests.LambdaMonolithContextClass
import com.intellij.remoteDev.tests.LambdaTestBridge
import com.intellij.remoteDev.tests.LambdaTestsConstants
import com.intellij.remoteDev.tests.impl.utils.SerializedLambdaWithIdeContextHelper
import com.intellij.remoteDev.tests.impl.utils.runLogged
import com.intellij.remoteDev.tests.impl.utils.waitSuspendingNotNull
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdIdeType
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdTestActionParameters
import com.intellij.remoteDev.tests.modelGenerated.lambdaTestModel
import com.jetbrains.rd.framework.IdKind
import com.jetbrains.rd.framework.Identities
import com.jetbrains.rd.framework.Protocol
import com.jetbrains.rd.framework.Serializers
import com.jetbrains.rd.framework.SocketWire
import com.jetbrains.rd.util.lifetime.EternalLifetime
import com.jetbrains.rd.util.reactive.viewNotNull
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.io.Serializable
import java.net.InetAddress
import java.net.URLClassLoader
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.Path
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.isSubclassOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@TestOnly
@ApiStatus.Internal
open class LambdaTestHost(coroutineScope: CoroutineScope) {
  companion object {
    // it is easier to sort out logs from just testFramework
    private val LOG
      get() = Logger.getInstance(RdctTestFrameworkLoggerCategory.category + "Host")

    fun getLambdaTestPort(): Int? =
      System.getProperty(LambdaTestsConstants.protocolPortPropertyName)?.toIntOrNull()

    val sourcesRootFolder: File by lazy {
      System.getProperty(LambdaTestsConstants.sourcePathProperty, PathManager.getHomePath()).let(::File)
    }

    /**
     * ID of the plugin which contains test code.
     * Currently, only test code of the client part is put to a separate plugin.
     */
    const val TEST_MODULE_ID_PROPERTY_NAME: String = "lambda.test.module.id"

    // TODO: plugin: PluginModuleDescriptor might be passed as a context parameter and not via constructor
    abstract class NamedLambda<T : LambdaIdeContext>(protected val lambdaIdeContext: T, protected val plugin: PluginModuleDescriptor) {
      fun name(): String = this::class.qualifiedName ?: error("Can't get qualified name of lambda $this")
      abstract suspend fun T.lambda(args: LambdaRdTestActionParameters): Any?
      suspend fun runLambda(args: LambdaRdTestActionParameters) {
        with(lambdaIdeContext) {
          lambda(args = args)
        }
      }
    }
  }

  init {
    val hostAddress =
      System.getProperty(LambdaTestsConstants.protocolHostPropertyName)?.let {
        LOG.info("${LambdaTestsConstants.protocolHostPropertyName} system property is set=$it, will try to get address from it.")
        // this won't work when we do custom network setups as the default gateway will be overridden
        // val hostEntries = File("/etc/hosts").readText().lines()
        // val dockerInterfaceEntry = hostEntries.last { it.isNotBlank() }
        // val ipAddress = dockerInterfaceEntry.split("\\s".toRegex()).first()
        //  host.docker.internal is not available on linux yet (20.04+)
        InetAddress.getByName(it)
      } ?: InetAddress.getLoopbackAddress()

    val port = getLambdaTestPort()
    if (port != null) {
      LOG.info("Queue creating protocol on $hostAddress:$port")
      coroutineScope.launch {
        val coroutineDumperOnTimeout = launch {
          delay(20.seconds)
          LOG.warn("LoadingState.COMPONENTS_LOADED has not occurred in 20 seconds: ${dumpCoroutines()}")
        }
        while (!LoadingState.COMPONENTS_LOADED.isOccurred) {
          delay(10.milliseconds)
        }
        coroutineDumperOnTimeout.cancel()
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          runLogged("Flush queue before tests") {
            withContext(Dispatchers.EDT) {
              IdeEventQueue.getInstance().flushQueue()
            }
          }
          createProtocol(hostAddress, port)
        }
      }
    }
  }

  private fun findLambdaClasses(
    lambdaReference: String,
    testModuleDescriptor: PluginModuleDescriptor,
    ideContext: LambdaIdeContext,
  ): List<NamedLambda<*>> {
    val className = if (lambdaReference.contains(".Companion")) {
      lambdaReference.substringBeforeLast(".").removeSuffix(".Companion")
    }
    else lambdaReference

    val testClass = Class.forName(className, true, testModuleDescriptor.pluginClassLoader).kotlin

    val companionClasses: Collection<KClass<*>> = testClass.companionObject?.nestedClasses ?: listOf()
    val nestedClasses: Collection<KClass<*>> = testClass.nestedClasses

    val namedLambdas = (companionClasses + nestedClasses + testClass)
      .filter { it.isSubclassOf(NamedLambda::class) }
      .mapNotNull {
        runCatching {
          //todo maybe we can filter out constuctor in a more clever way
          it.constructors.single().call(ideContext, testModuleDescriptor) as NamedLambda<*>
        }.getOrNull()
      }

    LOG.info("Found ${namedLambdas.size} lambda classes: ${namedLambdas.joinToString(", ") { it.name() }}")

    check(namedLambdas.isNotEmpty()) { "Can't find any named lambda in the test class '${testClass.qualifiedName}'" }

    return namedLambdas
  }

  private fun createProtocol(hostAddress: InetAddress, port: Int) {
    enableCoroutineDump()

    // EternalLifetime.createNested() is used intentionally to make sure logger session's lifetime is not terminated before the actual application stop.
    val lifetime = EternalLifetime.createNested()
    val protocolName = LambdaTestsConstants.protocolName
    LOG.info("Creating protocol '$protocolName' ...")

    val wire = SocketWire.Client(lifetime, LambdaTestIdeScheduler, port, protocolName, hostAddress)
    val protocol = Protocol(name = protocolName,
                            serializers = Serializers(),
                            identity = Identities(IdKind.Client),
                            scheduler = LambdaTestIdeScheduler,
                            wire = wire,
                            lifetime = lifetime)
    val model = protocol.lambdaTestModel

    LOG.info("Advise for session. Current state: ${model.session.value}...")
    model.session.viewNotNull(lifetime) { _, session ->

      try {
        @OptIn(ExperimentalCoroutinesApi::class)
        val sessionBgtDispatcher = Dispatchers.Default.limitedParallelism(1, "Lambda test session dispatcher")

        val testModuleDescriptor = run {
          val testModuleId = System.getProperty(TEST_MODULE_ID_PROPERTY_NAME)
                             ?: return@run null
          val tmd = PluginManagerCore.getPluginSet().findEnabledModule(PluginModuleId(testModuleId, PluginModuleId.JETBRAINS_NAMESPACE))
                    ?: error("Test plugin with test module '$testModuleId' is not found")

          assert(tmd.pluginClassLoader != null) {
            "Test plugin with test module '${testModuleId}' is not loaded." +
            "Probably due to missing dependencies, see `com.intellij.ide.plugins.ClassLoaderConfigurator#configureContentModule`."
          }
          return@run tmd
        }

        LOG.info("All test code will be loaded using '${testModuleDescriptor?.pluginClassLoader}'")

        fun getLambdaIdeContext(): LambdaIdeContextClass {
          val currentTestCoroutineScope = CoroutineScope(Dispatchers.Default + CoroutineName("Lambda test session scope") + SupervisorJob())

          currentTestCoroutineScope.coroutineContext.job.invokeOnCompletion {
            LOG.info("Test coroutine scope is completed")
          }
          return when (session.rdIdeType) {
            LambdaRdIdeType.BACKEND -> LambdaBackendContextClass(currentTestCoroutineScope.coroutineContext)
            LambdaRdIdeType.FRONTEND -> LambdaFrontendContextClass(currentTestCoroutineScope.coroutineContext)
            LambdaRdIdeType.MONOLITH -> LambdaMonolithContextClass(currentTestCoroutineScope.coroutineContext)
          }
        }

        var ideContext: LambdaIdeContextClass? = null

        session.beforeAll.setSuspend(sessionBgtDispatcher) { _, testClassName ->
          LOG.info("========================= Test class '$testClassName' started ==========================")
          assert(ideContext == null) { "Lambda task coroutine context should not be defined" }
        }

        session.beforeEach.setSuspend(sessionBgtDispatcher) { _, testName ->
          LOG.info("------------------------- Test '$testName' started -------------------------")
          runLogged("Flush queue in between tests", 30.seconds) {
            withContext(Dispatchers.EDT) {
              IdeEventQueue.getInstance().flushQueue()
            }
          }
          runLogged("Sync front and back protocol events", 10.seconds) {
            LambdaTestBridge.getInstance().syncProtocolEvents()
          }
          ideContext = getLambdaIdeContext()
        }

        session.afterEach.setSuspend(sessionBgtDispatcher) { _, testName ->
          LOG.info("------------------------- Test '$testName' finished -------------------------")
          assert(ideContext?.coroutineContext?.isActive == true) { "Lambda task coroutine context should be active" }
          try {
            ideContext!!.runAfterEachCleanup()

            runLogged("Cancelling scopes in after each", 20.seconds) {
              ideContext!!.coroutineContext.job.cancel()
            }

            makeSureNoModals()

            runLogged("Waiting scopes in after each are canceled", 20.seconds) {
              ideContext!!.coroutineContext.job.join()
            }
          }
          finally {
            ideContext = null
          }
        }

        session.afterAll.setSuspend(sessionBgtDispatcher) { _, testClassName ->
          LOG.info("========================= Test class '$testClassName' finished =========================")
          assert(ideContext == null) { "Lambda task coroutine context should not be defined" }
        }
        // Advice for processing events
        session.runLambda.setSuspend(sessionBgtDispatcher) { _, parameters ->
          LOG.info("'${parameters.reference}': received lambda execution request")

          assert(testModuleDescriptor != null) {
            "Test module descriptor is not set, can't find test class '${parameters.reference}'"
          }
          try {
            val lambdaReference = parameters.reference
            assert(ideContext?.coroutineContext?.isActive == true) { "Lambda task coroutine context should be active" }

            val namedLambdas = findLambdaClasses(lambdaReference, testModuleDescriptor!!, ideContext!!)

            val ideAction = namedLambdas.singleOrNull { it.name() == lambdaReference } ?: run {
              val text = "There is no Action with reference '${lambdaReference}', something went terribly wrong, " +
                         "all referenced actions: ${namedLambdas.map { it.name() }}"
              LOG.error(text)
              error(text)
            }

            assert(ClientId.current.isLocal) { "ClientId '${ClientId.current}' should be local before test method starts" }
            LOG.info("'$parameters': received action execution request")

            val providedCoroutineContext = Dispatchers.Default + CoroutineName("Lambda task: ${ideAction.name()}")
            val clientId = providedCoroutineContext.clientId() ?: ClientId.current

            withContext(providedCoroutineContext) {
              assert(ClientId.current == clientId) { "ClientId '${ClientId.current}' should equal $clientId one when test method starts" }

              runLogged(parameters.reference, 1.minutes) {
                ideAction.runLambda(parameters)
              }
            }
          }
          catch (ex: Throwable) {
            LOG.warn("${session.rdIdeType}: ${parameters.let { "'$it' " }}hasn't finished successfully", ex)
            throw ex
          }
        }

        // Advice for processing events
        session.runSerializedLambda.setSuspend(sessionBgtDispatcher) { _, lambda ->
          suspend fun clientIdContextToRunLambda() = if (session.rdIdeType == LambdaRdIdeType.BACKEND && AppMode.isRemoteDevHost()) {
            waitSuspendingNotNull("Got remote client id", 20.seconds) {
              ClientSessionsManager.getAppSessions(ClientKind.REMOTE).singleOrNull()?.clientId
            }.let { ClientIdContextElement(it) }
          }
          else {
            EmptyCoroutineContext
          }

          assert(ClientId.current.isLocal) { "ClientId '${ClientId.current}' should be local before test method starts" }

          val scopeToUse = if (lambda.globalTestScope) {
            getLambdaIdeContext()
          }
          else {
            assert(ideContext?.coroutineContext?.isActive == true) { "Lambda task coroutine context should be active" }
            ideContext!!
          }

          withContext(scopeToUse.coroutineContext + Dispatchers.Default + CoroutineName("Lambda task: ${lambda.stepName}") + clientIdContextToRunLambda()) {
            runLogged(lambda.stepName, 10.minutes) {
              val urls = lambda.classPath.map { Path(it).toUri().toURL() }
              URLClassLoader(urls.toTypedArray(), testModuleDescriptor?.pluginClassLoader ?: this::class.java.classLoader).use { cl ->
                SerializedLambdaWithIdeContextHelper().let { loader ->
                  val params = lambda.parametersBase64.map {
                    loader.decodeObject<String>(it, classLoader = cl) ?: error("Parameter $it is not serializable")
                  }
                  val serializableConsumer =
                    loader.getSuspendingSerializableConsumer<LambdaIdeContext, Any>(lambda.serializedDataBase64, classLoader = cl)
                  val result = with(serializableConsumer) {
                    with(scopeToUse) {
                      runSerializedLambda(params)
                    }
                  }
                  if (result is Serializable) {
                    loader.serialize(result)
                  }
                  else {
                    LOG.warn("Lambda '${lambda.stepName}' didn't return serializable result")
                    "<NO RESULT>"
                  }
                }
              }
            }
          }
        }

        session.isResponding.setSuspend(sessionBgtDispatcher + NonCancellable) { _, _ ->
          LOG.info("Answering for session is responding...")
          true
        }

        LOG.info("Test session ready!")
        session.ready.value = true
      }
      catch (ex: Throwable) {
        LOG.warn("Test session initialization hasn't finished successfully", ex)
        session.ready.value = false
      }
    }
  }

  private suspend fun makeSureNoModals(): Boolean =
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement() + NonCancellable) {
      repeat(10) {
        if (ModalityState.current() == ModalityState.nonModal()) {
          return@withContext true
        }
        delay(1.seconds)
      }
      LOG.warn("Unexpected modality: " + ModalityState.current())
      LaterInvocator.forceLeaveAllModals("${this@LambdaTestHost::class.java.simpleName} - makeSureNoModals")
      repeat(10) {
        if (ModalityState.current() == ModalityState.nonModal()) {
          return@withContext true
        }
        delay(1.seconds)
      }
      error("Failed to close modal dialog: " + ModalityState.current())
    }
}