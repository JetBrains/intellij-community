package com.intellij.driver.client

import com.intellij.driver.client.impl.DriverImpl
import com.intellij.driver.client.impl.JmxHost
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.model.ProductVersion
import org.jetbrains.annotations.Contract
import kotlin.reflect.KClass

/**
 * Provides a generic interface to call code in a running IDE instance, such as service and utility methods.
 * It connects to a process via JMX protocol and creates remote proxies for classes of the running IDE.
 *
 * To call any code you need to create an interface annotated with [Remote] annotation.
 * It must declare methods you need with the same name and number of parameters as the actual class in the IDE.
 *
 * Example:
 * ```kotlin
 * @Remote("com.intellij.psi.PsiManager")
 * interface PsiManager {
 *   fun findFile(file: VirtualFile): PsiFile?
 * }
 *
 * @Remote("com.intellij.openapi.vfs.VirtualFile")
 * interface VirtualFile {
 *   fun getName(): String
 * }
 *
 * @Remote("com.intellij.psi.PsiFile")
 * interface PsiFile
 * ```
 *
 * Then it can be used in the following call:
 * ```kotlin
 * driver.withReadAction {
 *   val psiFile = service<PsiManager>(project).findFile(file)
 * }
 * ```
 *
 * Supported types of method parameters and result:
 * - primitives and their wrappers [Integer], [Short], [Long], [Double], [Float], [Byte]
 * - [String]
 * - [Remote] reference
 * - Array of primitive values, [String] or [Remote] references
 * - Collection of primitive values, [String] or [Remote] references
 *
 * To use classes that are not primitives, you create the corresponding [Remote] mapped interface and
 * use it instead of the original types in method signatures.
 *
 * If a plugin (not platform) declares a required service/utility, you must specify the plugin identifier in [Remote.plugin] attribute.
 *
 * Only *public* methods can be called. Private, package-private and protected methods are supposed to be changed to public.
 * Mark methods with [org.jetbrains.annotations.VisibleForTesting] to show that they are used from tests.
 *
 * Service and utility proxies can be acquired on each call, there is no need to cache them in clients.
 *
 * Any IDE class may have as many different [Remote] mapped interfaces as needed, you can declare another one
 * if the standard SDK does not provide the required method.
 *
 * @see Driver.create
 * @see Remote
 */
interface Driver : AutoCloseable {
  /**
   * @return whether the Driver can access a remote process via JMX.
   */
  val isConnected: Boolean

  /**
   * @return information about the product under test
   */
  fun getProductVersion(): ProductVersion

  /**
   * Forcefully exits the application.
   */
  fun exitApplication()

  /**
   * Screenshots and saves images of application windows to the test output folder.
   *
   * @param outFolder name of the test output folder relative to logs directory
   */
  fun takeScreenshot(outFolder: String?)

  /**
   * @return new remote proxy for a [Remote] application service interface
   */
  @Contract(pure = true)
  fun <T : Any> service(clazz: KClass<T>): T

  /**
   * @return new remote proxy for a [Remote] project service interface
   */
  @Contract(pure = true)
  fun <T : Any> service(clazz: KClass<T>, project: ProjectRef): T

  /**
   * @return new remote proxy for a utility class or a class with static methods
   */
  @Contract(pure = true)
  fun <T : Any> utility(clazz: KClass<T>): T

  /**
   * @return proxy reference for a newly created remote object
   */
  fun <T : Any> new(clazz: KClass<T>, vararg args: Any?): T

  /**
   * Assumes that the remote reference corresponds to another type. Performs unsafe cast.
   */
  fun <T : Any> cast(instance: Any, clazz: KClass<T>): T

  /**
   * Runs the block with the specified dispatcher and lock semantics.
   */
  fun <T> withContext(dispatcher: OnDispatcher = OnDispatcher.DEFAULT,
                      semantics: LockSemantics = LockSemantics.NO_LOCK,
                      code: Driver.() -> T): T

  /**
   * Runs the block that requires a read action with the specified dispatcher.
   */
  fun <T> withReadAction(dispatcher: OnDispatcher = OnDispatcher.DEFAULT,
                         code: Driver.() -> T): T

  /**
   * Runs the block that requires a write action.
   */
  fun <T> withWriteAction(code: Driver.() -> T): T

  companion object {
    /**
     * Creates a driver with the specified remote endpoint. Actual JMX connection performed lazily on the first call to the remote side.
     * You must call [Driver.close] to explicitly close the connection and dispose its resources.
     */
    @JvmStatic
    @Contract(pure = true)
    fun create(host: JmxHost? = JmxHost(null, null, "localhost:7777")): Driver {
      return DriverImpl(host)
    }
  }
}

/**
 * Remote reference to a Project.
 */
interface ProjectRef

/**
 * @return new remote proxy for a [Remote] application service interface
 */
inline fun <reified T : Any> Driver.service(): T {
  return service(T::class)
}

/**
 * @return new remote proxy for a [Remote] application service interface
 */
inline fun <reified T : Any> Driver.service(project: ProjectRef): T {
  return service(T::class, project)
}

/**
 * @return new remote proxy for a utility class or a class with static methods
 */
inline fun <reified T : Any> Driver.utility(): T {
  return utility(T::class)
}