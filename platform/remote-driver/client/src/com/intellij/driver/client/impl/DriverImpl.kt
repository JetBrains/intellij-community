package com.intellij.driver.client.impl

import com.intellij.driver.client.*
import com.intellij.driver.model.*
import com.intellij.driver.model.transport.*
import java.awt.IllegalComponentStateException
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy
import java.lang.reflect.UndeclaredThrowableException
import java.util.concurrent.ConcurrentHashMap
import javax.management.AttributeNotFoundException
import javax.management.InstanceNotFoundException
import kotlin.reflect.KClass

open class DriverImpl(host: JmxHost?, override val isRemDevMode: Boolean) : Driver {
  private val invoker: Invoker = JmxCallHandler.jmx(Invoker::class.java, host)
  private val sessionHolder = ThreadLocal<Session>()

  private val appServices: MutableMap<AppServiceId, Any> = ConcurrentHashMap()
  private val projectServices: MutableMap<ProjectServiceId, Any> = ConcurrentHashMap()
  private val utils: MutableMap<UtilityId, Any> = ConcurrentHashMap()

  protected open val polymorphRegistry: PolymorphRefRegistry? = null

  override val isConnected: Boolean
    get() {
      try {
        return invoker.isApplicationInitialized()
      }
      catch (ut: UndeclaredThrowableException) {
        if (ut.cause is InstanceNotFoundException
            || ut.cause is AttributeNotFoundException) {
          return false // Invoker is not yet registered in JMX
        }
        throw ut
      }
      catch (ioe: JmxCallException) {
        return false
      }
    }

  fun getInvoker() = invoker

  override fun getProductVersion(): ProductVersion {
    return invoker.getProductVersion()
  }

  override fun exitApplication() {
    invoker.exit()
  }

  override fun takeScreenshot(outFolder: String?): String? {
    return invoker.takeScreenshot(outFolder)
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> service(clazz: KClass<T>, rdTarget: RdTarget): T {
    return appServices.computeIfAbsent(AppServiceId(clazz.java, rdTarget)) { serviceBridge(clazz.java, null, rdTarget) } as T
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> service(clazz: KClass<T>, project: ProjectRef?, rdTarget: RdTarget): T {
    val id = ProjectServiceId((project as RefWrapper).getRef().identityHashCode, clazz.java, rdTarget)
    return projectServices.computeIfAbsent(id) { serviceBridge(clazz.java, project, rdTarget) } as T
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> utility(clazz: KClass<T>, rdTarget: RdTarget): T {
    return utils.computeIfAbsent(UtilityId(clazz.java, rdTarget)) { utilityBridge(clazz.java, rdTarget) } as T
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> new(clazz: KClass<T>, vararg args: Any?, rdTarget: RdTarget): T {
    val remote = findRemoteMeta(clazz.java)
                 ?: throw IllegalArgumentException("Class $clazz is not annotated with @Remote annotation")

    val rdTarget = mergeRdTargets(rdTarget, remote, *args)
    val (sessionId, dispatcher, semantics) = sessionHolder.get() ?: NO_SESSION
    val call = NewInstanceCall(
      sessionId,
      null,
      getPluginId(remote),
      dispatcher,
      semantics,
      remote.value,
      rdTarget,
      convertArgsToPass(rdTarget, args)
    )
    val callResult = makeCall(call)
    return convertResult(callResult, clazz.java, getPluginId(remote)) as T
  }

  override fun <T : Any> cast(instance: Any, clazz: KClass<T>): T {
    if (instance !is RefWrapper) {
      throw IllegalArgumentException("$instance not a Ref to remote instance")
    }

    val ref = instance.getRef()

    val targetClassPluginId = findRemoteMeta(clazz.java)?.let { getPluginId(it) }
    val refPluginId = instance.getRefPluginId()

    @Suppress("UNCHECKED_CAST")
    return refBridge(clazz.java, ref, targetClassPluginId ?: refPluginId) as T
  }

  private fun convertArgsToPass(rdTarget: RdTarget, args: Array<out Any?>?): Array<Any?> {
    if (args == null) return emptyArray()

    return args
      .map { arg ->
        when (arg) {
          is Array<*> -> arg.map { convertArgToPass(it, rdTarget) }.toTypedArray()
          is List<*> -> arg.map { convertArgToPass(it, rdTarget) }
          else -> convertArgToPass(arg, rdTarget)
        }
      }
      .toTypedArray()
  }

  private fun convertArgToPass(arg: Any?, rdTarget: RdTarget): Any? {
    var result = arg
    if (result is PolymorphRef && polymorphRegistry != null) {
      result = polymorphRegistry?.convert(result, rdTarget)
    }
    if (result is RefWrapper) {
      result = result.getRef()
    }
    return result
  }

  private fun convertResult(callResult: RemoteCallResult, targetClass: Class<*>, pluginId: String?): Any? {
    val value = callResult.value ?: return null

    if (value is Ref) {
      return refBridge(targetClass, value, pluginId)
    }

    return null
  }

  private fun convertResult(callResult: RemoteCallResult, method: Method, pluginId: String?): Any? {
    val value = callResult.value ?: return null

    if (RemoteCall.isPassByValue(value)) {
      return value
    }

    if (value is Ref) {
      if (Ref::class.java.isAssignableFrom(method.returnType)) return value

      return refBridge(method.returnType, value, pluginId)
    }

    if (value is RefList) {
      if (method.returnType == RefList::class.java) return value

      if (Collection::class.java.isAssignableFrom(method.returnType)) {
        val parameterizedType = method.genericReturnType as? ParameterizedType ?: return value.items
        val componentType = parameterizedType.actualTypeArguments.firstOrNull() as? Class<*>
                            ?: return value.items

        return value.items.map { refBridge(componentType, it, pluginId) }
      }

      if (method.returnType.isArray) {
        val componentType = method.returnType.componentType
        val array = java.lang.reflect.Array.newInstance(componentType, value.items.size)
        for ((i, item) in value.items.withIndex()) {
          java.lang.reflect.Array.set(array, i, refBridge(componentType, item, pluginId))
        }
        return array
      }

      return value
    }

    return value // let's hope we will be able to cast it
  }

  private fun getPluginId(remote: Remote): String? {
    return remote.plugin.takeIf { it.isNotBlank() }
  }

  private fun serviceBridge(clazz: Class<*>, project: ProjectRef? = null, rdTarget: RdTarget): Any {
    val remote = findRemoteMeta(clazz) ?: throw notAnnotatedError(clazz)

    return Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz)) { proxy: Any?, method: Method, args: Array<Any?>? ->
      when (method.name) {
        "equals" -> proxy === args?.firstOrNull()
        "hashCode" -> clazz.hashCode()
        "toString" -> "@Service(APP) " + remote.value
        else -> {
          val rdTarget = mergeRdTargets(rdTarget, remote, project, *(args ?: emptyArray()))
          val declaredLockSemantics = method.annotations.filterIsInstance<RequiresLockSemantics>().singleOrNull()?.lockSemantics
          val (sessionId, dispatcher, sessionLockSemantics) = sessionHolder.get() ?: NO_SESSION
          val call = ServiceCall(
            sessionId,
            findTimedMeta(method)?.value,
            getPluginId(remote),
            dispatcher,
            declaredLockSemantics ?: sessionLockSemantics,
            remote.value,
            method.name,
            convertArgsToPass(rdTarget, args),
            (project as? RefWrapper?)?.getRef(),
            remote.serviceInterface.takeIf { it.isNotBlank() },
            rdTarget
          )
          val callResult = makeCall(call)
          convertResult(callResult, method, getPluginId(remote))
        }
      }
    }
  }

  private fun makeCall(call: RemoteCall): RemoteCallResult {
    return try {
      invoker.invoke(call)
    }
    catch (ise: IllegalComponentStateException) {
      throw ise
    }
    catch (ed: DriverIllegalStateException) {
      throw ed
    }
    catch (e: Exception) {
      throw DriverCallException("Error on remote driver call", e)
    }
  }

  private fun utilityBridge(clazz: Class<*>, rdTarget: RdTarget): Any {
    val remote = findRemoteMeta(clazz) ?: throw notAnnotatedError(clazz)

    return Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz)) { proxy: Any?, method: Method, args: Array<Any?>? ->
      when (method.name) {
        "equals" -> proxy === args?.firstOrNull()
        "hashCode" -> clazz.hashCode()
        "toString" -> "Utility " + remote.value
        else -> {
          val rdTarget = mergeRdTargets(rdTarget, remote, *(args ?: emptyArray()))
          val declaredLockSemantics = method.annotations.filterIsInstance<RequiresLockSemantics>().singleOrNull()?.lockSemantics
          val (sessionId, dispatcher, sessionLockSemantics) = sessionHolder.get() ?: NO_SESSION
          val call = UtilityCall(
            sessionId,
            findTimedMeta(method)?.value,
            getPluginId(remote),
            dispatcher,
            declaredLockSemantics ?: sessionLockSemantics,
            remote.value,
            method.name,
            rdTarget,
            convertArgsToPass(rdTarget, args),
          )
          val callResult = makeCall(call)
          convertResult(callResult, method, getPluginId(remote))
        }
      }
    }
  }

  private fun refBridge(clazz: Class<*>, ref: Ref, pluginId: String?): Any {
    val remote = findRemoteMeta(clazz) ?: throw notAnnotatedError(clazz)

    return Proxy.newProxyInstance(clazz.classLoader,
                                  arrayOf(clazz, RefWrapper::class.java)) { proxy: Any?, method: Method, args: Array<Any?>? ->
      when (method.name) {
        "equals" -> proxy === args?.firstOrNull()
        "hashCode" -> ref.identityHashCode
        "toString" -> ref.asString
        "getRef" -> ref
        "getRefPluginId" -> pluginId
        else -> {
          val (sessionId, dispatcher, semantics) = sessionHolder.get() ?: NO_SESSION
          val call = RefCall(
            sessionId,
            findTimedMeta(method)?.value,
            pluginId ?: getPluginId(remote),
            dispatcher,
            semantics,
            remote.value,
            method.name,
            convertArgsToPass(ref.rdTarget(), args),
            ref
          )
          val callResult = makeCall(call)
          convertResult(callResult, method, pluginId)
        }
      }
    }
  }

  override fun <T> withContext(dispatcher: OnDispatcher,
                               semantics: LockSemantics,
                               code: Driver.() -> T): T {
    val currentValue = sessionHolder.get()
    val runAsSession = Session(currentValue?.id ?: invoker.newSession(), dispatcher, semantics)
    sessionHolder.set(runAsSession)
    return try {
      this.code()
    }
    finally {
      if (currentValue != null) {
        sessionHolder.set(currentValue)
      }
      else {
        try {
          invoker.cleanup(runAsSession.id)
        }
        catch (e: JmxCallException) {
          System.err.println("Unable to cleanup remote Driver session")
        }

        sessionHolder.remove()
      }
    }
  }

  override fun <T> withWriteAction(code: Driver.() -> T): T {
    return withContext(OnDispatcher.EDT, LockSemantics.WRITE_ACTION, code)
  }

  override fun close() {
    try {
      invoker.close()
    }
    catch (t: Throwable) {
      System.err.println("Error on close of JMX session")
      t.printStackTrace()
    }
  }

  override fun <T> withReadAction(dispatcher: OnDispatcher, code: Driver.() -> T): T {
    return withContext(dispatcher, LockSemantics.READ_ACTION, code)
  }
}

private fun notAnnotatedError(clazz: Class<*>): IllegalArgumentException {
  return IllegalArgumentException("Class $clazz is not annotated with @Remote annotation")
}

private fun findTimedMeta(method: Method): Timed? {
  return method.annotations
    .filterIsInstance<Timed>()
    .firstOrNull()
}

private fun findRemoteMeta(clazz: Class<*>): Remote? {
  return clazz.annotations
    .filterIsInstance<Remote>()
    .firstOrNull()
}

private fun mergeRdTargets(
  forceRdTarget: RdTarget,
  remote: Remote,
  vararg args: Any?
): RdTarget {
  val argsRdTargets = args.filterIsInstance<RefWrapper>().filter { it !is PolymorphRef }
    .map { it.getRef().rdTarget() }

  return (argsRdTargets + forceRdTarget + remote.rdTarget).reduce { acc, b ->
    if (acc == RdTarget.DEFAULT) b
    else if (b == RdTarget.DEFAULT) acc
    else if (acc == b) acc
    else throw IllegalStateException("Inconsistent rdTargets. Use can not request service with non default RtTarget as service of another non default RtTarget." +
                                     "Consider introducing a separate service or changing type of the service to RdDefault." +
                                     "ForceRdTarget=$forceRdTarget " +
                                     "Remote(value=${remote.value}, rdTarget=${remote.rdTarget}), " +
                                     "Args: [${args.filterIsInstance<RefWrapper>().map { "${it.getRef().rdTarget()} -> $it" }.joinToString()}]"
    )
  }.let {
    if (it == RdTarget.DEFAULT) RdTarget.FRONTEND else it
  }
}

internal data class Session(
  val id: Int,
  val dispatcher: OnDispatcher,
  val semantics: LockSemantics
)

private val NO_SESSION: Session = Session(0, OnDispatcher.DEFAULT, LockSemantics.NO_LOCK)

class DriverCallException(message: String, e: Throwable) : RuntimeException(message, e)

private data class AppServiceId(val clazz: Class<*>, val rdTarget: RdTarget)
private data class ProjectServiceId(val projectId: Int, val serviceClass: Class<*>, val rdTarget: RdTarget)
private data class UtilityId(val clazz: Class<*>, val rdTarget: RdTarget)

@JmxName("com.intellij.driver:type=Invoker")
interface Invoker : AutoCloseable {
  fun getProductVersion(): ProductVersion

  fun isApplicationInitialized(): Boolean

  fun exit()

  fun invoke(call: RemoteCall): RemoteCallResult

  fun newSession(): Int

  fun newSession(id: Int): Int

  fun cleanup(sessionId: Int)

  fun takeScreenshot(folder: String?): String?
}
