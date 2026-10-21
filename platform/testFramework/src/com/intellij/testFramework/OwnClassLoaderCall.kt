// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.util.ref.GCWatcher
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.function.Function
import java.util.function.Supplier

/** The result of [callUnderOwnClassLoader], and a watcher of the class loader that defined the caller of the action. */
@TestOnly
@ApiStatus.Internal
class OwnClassLoaderCall<T>(val result: T, val loader: GCWatcher)

/**
 * Calls [action] under the frame of a class that a class loader of its own defines, as the code of a plugin calls the platform.
 *
 * A test keeps [OwnClassLoaderCall.result] alive and calls `loader.ensureCollected()`. A result that keeps the stack of its creation
 * as a [Throwable] keeps that class loader, because the backtrace of the Throwable holds the class of each frame, so the check fails.
 * A result that keeps [StackTraceElement] data keeps names only, so the check passes.
 */
@TestOnly
@ApiStatus.Internal
fun <T> callUnderOwnClassLoader(action: () -> T): OwnClassLoaderCall<T> {
  val loader = OwnCallerClassLoader(OwnClassLoaderCaller::class.java.classLoader)
  @Suppress("UNCHECKED_CAST")
  val caller = loader.loadClass(OwnClassLoaderCaller::class.java.name).getConstructor().newInstance() as Function<Supplier<T>, T>
  return OwnClassLoaderCall(caller.apply(Supplier(action)), GCWatcher.tracking(loader))
}

/** Calls the action that it gets. [OwnCallerClassLoader] defines its own copy of this class. */
internal class OwnClassLoaderCaller : Function<Supplier<Any?>, Any?> {
  override fun apply(action: Supplier<Any?>): Any? = action.get()
}

/** Defines [OwnClassLoaderCaller] itself and delegates every other class to its parent, as a plugin class loader does. */
private class OwnCallerClassLoader(parent: ClassLoader) : ClassLoader(parent) {
  override fun loadClass(name: String, resolve: Boolean): Class<*> {
    if (name != OwnClassLoaderCaller::class.java.name) {
      return super.loadClass(name, resolve)
    }
    return synchronized(getClassLoadingLock(name)) {
      findLoadedClass(name) ?: run {
        val bytes = parent.getResourceAsStream(name.replace('.', '/') + ".class")!!.use { it.readBytes() }
        defineClass(name, bytes, 0, bytes.size)
      }
    }
  }
}
