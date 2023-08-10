package com.intellij.driver.client

import com.intellij.driver.client.impl.DriverImpl
import com.intellij.driver.client.impl.JmxHost
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.model.ProductVersion
import org.jetbrains.annotations.Contract
import kotlin.reflect.KClass

interface ProjectRef

interface Driver : AutoCloseable {
  val isConnected: Boolean

  fun getProductVersion(): ProductVersion

  /**
   * Forcefully exits the application
   */
  fun exitApplication()

  @Contract(pure = true)
  fun <T : Any> service(clazz: KClass<T>): T

  @Contract(pure = true)
  fun <T : Any> service(clazz: KClass<T>, project: ProjectRef): T

  @Contract(pure = true)
  fun <T : Any> utility(clazz: KClass<T>): T

  fun <T : Any> new(clazz: KClass<T>, vararg args: Any?): T

  fun <T : Any> cast(instance: Any, clazz: KClass<T>): T

  fun <T> withContext(dispatcher: OnDispatcher = OnDispatcher.DEFAULT,
                      semantics: LockSemantics = LockSemantics.NO_LOCK,
                      code: Driver.() -> T): T

  fun <T> withReadAction(dispatcher: OnDispatcher = OnDispatcher.DEFAULT,
                         code: Driver.() -> T): T

  fun <T> withWriteAction(code: Driver.() -> T): T

  companion object {
    @JvmStatic
    @Contract(pure = true)
    fun create(host: JmxHost? = JmxHost(null, null, "localhost:7777")): Driver {
      return DriverImpl(host)
    }
  }
}
