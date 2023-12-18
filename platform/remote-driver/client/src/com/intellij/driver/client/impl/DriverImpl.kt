package com.intellij.driver.client.impl

import com.intellij.driver.client.Driver
import com.intellij.driver.client.ProjectRef
import com.intellij.driver.client.Remote
import com.intellij.driver.client.Timed
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.model.ProductVersion
import com.intellij.driver.model.transport.*
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy
import java.lang.reflect.UndeclaredThrowableException
import java.util.concurrent.ConcurrentHashMap
import javax.management.AttributeNotFoundException
import javax.management.InstanceNotFoundException
import kotlin.reflect.KClass

internal class DriverImpl(host: JmxHost?) : Driver {
  private val invoker: Invoker = JmxCallHandler.jmx(Invoker::class.java, host)
  private val sessionHolder = ThreadLocal<Session>()

  private val appServices: MutableMap<Class<*>, Any> = ConcurrentHashMap()
  private val projectServices: MutableMap<ProjectRef, MutableMap<Class<*>, Any>> = ConcurrentHashMap()
  private val utils: MutableMap<Class<*>, Any> = ConcurrentHashMap()

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

  override fun getProductVersion(): ProductVersion {
    return invoker.getProductVersion()
  }

  override fun exitApplication() {
    invoker.exit()
  }

  override fun takeScreenshot(outFolder: String?) {
    invoker.takeScreenshot(outFolder)
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> service(clazz: KClass<T>): T {
    return appServices.computeIfAbsent(clazz.java, ::serviceBridge) as T
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> service(clazz: KClass<T>, project: ProjectRef): T {
    val projectServices = projectServices.computeIfAbsent(project) { ConcurrentHashMap() }
    return projectServices.computeIfAbsent(clazz.java) { serviceBridge(clazz.java, project) } as T
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> utility(clazz: KClass<T>): T {
    return utils.computeIfAbsent(clazz.java, ::utilityBridge) as T
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> new(clazz: KClass<T>, vararg args: Any?): T {
    val remote = findRemoteMeta(clazz.java)
                 ?: throw IllegalArgumentException("Class $clazz is not annotated with @Remote annotation")

    val (sessionId, dispatcher, semantics) = sessionHolder.get() ?: NO_SESSION
    val call = NewInstanceCall(
      sessionId,
      null,
      getPluginId(remote),
      dispatcher,
      semantics,
      remote.value,
      convertArgsToPass(args)
    )
    val callResult = makeCall(call)
    return convertResult(callResult, clazz.java, getPluginId(remote)) as T
  }

  override fun <T : Any> cast(instance: Any, clazz: KClass<T>): T {
    if (instance !is RefWrapper) throw IllegalArgumentException("instance not a Ref to remote instance")

    val ref = instance.getRef()
    val refPluginId = instance.getRefPluginId()

    @Suppress("UNCHECKED_CAST")
    return refBridge(clazz.java, ref, refPluginId) as T
  }

  private fun convertArgsToPass(args: Array<out Any?>?): Array<Any?> {
    if (args == null) return emptyArray()

    return args
      .map { if (it is RefWrapper) it.getRef() else it }
      .toTypedArray()
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

  private fun serviceBridge(clazz: Class<*>): Any {
    val remote = findRemoteMeta(clazz) ?: throw notAnnotatedError(clazz)

    return Proxy.newProxyInstance(getClassLoader(), arrayOf(clazz)) { proxy: Any?, method: Method, args: Array<Any?>? ->
      when (method.name) {
        "equals" -> proxy === args?.firstOrNull()
        "hashCode" -> clazz.hashCode()
        "toString" -> "@Service " + remote.value
        else -> {
          val (sessionId, dispatcher, semantics) = sessionHolder.get() ?: NO_SESSION
          val call = ServiceCall(
            sessionId,
            findTimedMeta(method)?.value,
            getPluginId(remote),
            dispatcher,
            semantics,
            remote.value,
            method.name,
            convertArgsToPass(args),
            null,
            remote.serviceInterface.takeIf { it.isNotBlank() }
          )
          val callResult = makeCall(call)
          convertResult(callResult, method, getPluginId(remote))
        }
      }
    }
  }

  private fun getPluginId(remote: Remote): String? {
    return remote.plugin.takeIf { it.isNotBlank() }
  }

  private fun serviceBridge(clazz: Class<*>, project: ProjectRef): Any {
    val remote = findRemoteMeta(clazz) ?: throw notAnnotatedError(clazz)

    return Proxy.newProxyInstance(getClassLoader(), arrayOf(clazz)) { proxy: Any?, method: Method, args: Array<Any?>? ->
      when (method.name) {
        "equals" -> proxy === args?.firstOrNull()
        "hashCode" -> clazz.hashCode()
        "toString" -> "@Service(APP) " + remote.value
        else -> {
          val (sessionId, dispatcher, semantics) = sessionHolder.get() ?: NO_SESSION
          val call = ServiceCall(
            sessionId,
            findTimedMeta(method)?.value,
            getPluginId(remote),
            dispatcher,
            semantics,
            remote.value,
            method.name,
            convertArgsToPass(args),
            (project as RefWrapper).getRef(),
            remote.serviceInterface.takeIf { it.isNotBlank() }
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
    catch (e: Exception) {
      throw DriverCallException("Error on remote driver call", e)
    }
  }

  private fun utilityBridge(clazz: Class<*>): Any {
    val remote = findRemoteMeta(clazz) ?: throw notAnnotatedError(clazz)

    return Proxy.newProxyInstance(getClassLoader(), arrayOf(clazz)) { proxy: Any?, method: Method, args: Array<Any?>? ->
      when (method.name) {
        "equals" -> proxy === args?.firstOrNull()
        "hashCode" -> clazz.hashCode()
        "toString" -> "Utility " + remote.value
        else -> {
          val (sessionId, dispatcher, semantics) = sessionHolder.get() ?: NO_SESSION
          val call = UtilityCall(
            sessionId,
            findTimedMeta(method)?.value,
            getPluginId(remote),
            dispatcher,
            semantics,
            remote.value,
            method.name,
            convertArgsToPass(args)
          )
          val callResult = makeCall(call)
          convertResult(callResult, method, getPluginId(remote))
        }
      }
    }
  }

  private fun refBridge(clazz: Class<*>, ref: Ref, pluginId: String?): Any {
    val remote = findRemoteMeta(clazz) ?: throw notAnnotatedError(clazz)

    return Proxy.newProxyInstance(getClassLoader(),
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
            convertArgsToPass(args),
            ref
          )
          val callResult = makeCall(call)
          convertResult(callResult, method, getPluginId(remote))
        }
      }
    }
  }

  private fun notAnnotatedError(clazz: Class<*>): IllegalArgumentException {
    return IllegalArgumentException("Class $clazz is not annotated with @Remote annotation")
  }

  private fun getClassLoader(): ClassLoader? {
    return javaClass.classLoader
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
    catch (t: Throwable) {
      invoker.takeScreenshot("beforeKill")
      throw t
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
    } catch (t: Throwable) {
      System.err.println("Error on close of JMX session")
      t.printStackTrace()
    }
  }

  override fun <T> withReadAction(dispatcher: OnDispatcher, code: Driver.() -> T): T {
    return withContext(dispatcher, LockSemantics.READ_ACTION, code)
  }
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

internal data class Session(
  val id: Int,
  val dispatcher: OnDispatcher,
  val semantics: LockSemantics
)

private val NO_SESSION: Session = Session(0, OnDispatcher.DEFAULT, LockSemantics.NO_LOCK)

@JmxName("com.intellij.driver:type=Invoker")
internal interface Invoker : AutoCloseable {
  fun getProductVersion(): ProductVersion

  fun isApplicationInitialized(): Boolean

  fun exit()

  fun invoke(call: RemoteCall): RemoteCallResult

  fun newSession(): Int

  fun cleanup(sessionId: Int)

  fun takeScreenshot(folder: String?)
}

class DriverCallException(message: String, e: Throwable) : RuntimeException(message, e)