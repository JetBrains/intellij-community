// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.util

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.github.api.GithubApiRequest
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GithubResponsePage
import java.io.IOException
import java.util.function.Predicate

object GithubApiPagesLoader {

  @Throws(IOException::class)
  @JvmStatic
  fun <T> loadAll(executor: GithubApiRequestExecutor, indicator: ProgressIndicator, pagesRequest: Request<T>): List<T> {
    val result = mutableListOf<T>()
    var request: GithubApiRequest<GithubResponsePage<T>>? = pagesRequest.initialRequest
    while (request != null) {
      val page = executor.execute(indicator, request)
      result.addAll(page.items)
      request = page.nextLink?.let(pagesRequest.urlRequestProvider)
    }
    return result
  }

  @Throws(IOException::class)
  @JvmStatic
  fun <T> find(executor: GithubApiRequestExecutor, indicator: ProgressIndicator, pagesRequest: Request<T>, predicate: Predicate<T>): T? {
    var request: GithubApiRequest<GithubResponsePage<T>>? = pagesRequest.initialRequest
    while (request != null) {
      val page = executor.execute(indicator, request)
      page.items.find { predicate.test(it) }?.let { return it }
      request = page.nextLink?.let(pagesRequest.urlRequestProvider)
    }
    return null
  }

  class Request<T>(val initialRequest: GithubApiRequest<GithubResponsePage<T>>,
                   val urlRequestProvider: (String) -> GithubApiRequest<GithubResponsePage<T>>)
}