// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.diagnostic.PluginException
import com.intellij.ide.IdeBundle
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsContexts.DialogMessage
import io.netty.handler.codec.http.QueryStringDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

abstract class JBProtocolCommand(private val command: String) {
  companion object {
    const val SCHEME = "jetbrains"
    const val FRAGMENT_PARAM_NAME = "__fragment"

    private val EP_NAME = ExtensionPointName<JBProtocolCommand>("com.intellij.jbProtocolCommand")

    @ApiStatus.Internal
    suspend fun execute(query: String): @DialogMessage String? {
      val decoder = QueryStringDecoder(query)
      val parts = decoder.path().split('/').dropLastWhile { it.isEmpty() }
      require(parts.size >= 2) { query }  // expected: at least a platform prefix and a command name
      val commandName = parts[1]
      val command = EP_NAME.lazySequence().find { it.command == commandName }
      return if (command != null) {
        val target = if (parts.size > 2) parts[2] else null
        val parameters = LinkedHashMap<String, String>()
        for ((key, list) in decoder.parameters()) {
          parameters[key] = if (list.isEmpty()) "" else list[list.size - 1]
        }
        val fragmentStart = query.lastIndexOf('#')
        val fragment = if (fragmentStart > 0) query.substring(fragmentStart + 1) else null
        command.execute(target, parameters, fragment)
      }
      else {
        IdeBundle.message("jb.protocol.unknown.command", commandName)
      }
    }
  }

  @Suppress("unused")
  @Deprecated("please implement {@link #perform(String, Map, String)} instead", ReplaceWith("perform(String, Map, String)"))
  open fun perform(target: String?, parameters: Map<String, String?>) {
    throw PluginException.createByClass(UnsupportedOperationException(), javaClass)
  }

  /**
   * The method should return a future with the command execution result - `null` when successful,
   * sentence-capitalized localized string in case of an error (see `"ide.protocol.*"` strings in `IdeBundle`).
   *
   * @see .parameter
   */
  open fun perform(target: String?, parameters: Map<String, String>, fragment: String?): Future<String?> {
    @Suppress("DEPRECATION")
    perform(target, mapOf(FRAGMENT_PARAM_NAME to fragment))
    return CompletableFuture.completedFuture(null)
  }

  protected open suspend fun execute(target: String?, parameters: Map<String, String>, fragment: String?): String? {
    val result = withContext(Dispatchers.EDT) { perform(target, parameters, fragment) }
    return if (result is CompletableFuture) result.asDeferred().await() else withContext(Dispatchers.IO) { result.get() }
  }

  protected fun parameter(parameters: Map<String, String>, name: String): String {
    val value = parameters[name]
    require(!value.isNullOrBlank()) { IdeBundle.message("jb.protocol.parameter.missing", name) }
    return value
  }
}
