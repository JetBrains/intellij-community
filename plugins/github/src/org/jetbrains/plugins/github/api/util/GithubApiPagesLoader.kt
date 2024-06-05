// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.util

import com.intellij.collaboration.api.page.foldToList
import com.intellij.openapi.progress.ProgressIndicator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.GithubApiRequest
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GithubResponsePage
import org.jetbrains.plugins.github.api.executeSuspend
import java.io.IOException
import java.util.function.Predicate

@ApiStatus.Internal
object GithubApiPagesLoader {

  @Throws(IOException::class)
  @JvmStatic
  fun <T> loadAll(executor: GithubApiRequestExecutor, indicator: ProgressIndicator, pagesRequest: Request<T>): List<T> {
    val result = mutableListOf<T>()
    loadAll(executor, indicator, pagesRequest) { result.addAll(it) }
    return result
  }

  @Throws(IOException::class)
  @JvmStatic
  suspend fun <T> loadAll(executor: GithubApiRequestExecutor, pagesRequest: Request<T>): List<T> =
    batchesFlow(executor, pagesRequest).foldToList()

  @Throws(IOException::class)
  @JvmStatic
  fun <T> loadAll(executor: GithubApiRequestExecutor,
                  indicator: ProgressIndicator,
                  pagesRequest: Request<T>,
                  pageItemsConsumer: (List<T>) -> Unit) {
    var request: GithubApiRequest<GithubResponsePage<T>>? = pagesRequest.initialRequest
    while (request != null) {
      val page = executor.execute(indicator, request)
      pageItemsConsumer(page.items)
      request = page.nextLink?.let(pagesRequest.urlRequestProvider)
    }
  }

  @Throws(IOException::class)
  @JvmStatic
  fun <T> batchesFlow(executor: GithubApiRequestExecutor, pagesRequest: Request<T>): Flow<List<T>> = flow {
    var request: GithubApiRequest<GithubResponsePage<T>>? = pagesRequest.initialRequest
    while (request != null) {
      val page = executor.executeSuspend(request)
      emit(page.items)
      request = page.nextLink?.let(pagesRequest.urlRequestProvider)
    }
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

  @Throws(IOException::class)
  @JvmStatic
  fun <T> load(executor: GithubApiRequestExecutor, indicator: ProgressIndicator, pagesRequest: Request<T>, maximum: Int): List<T> {
    val result = mutableListOf<T>()
    var request: GithubApiRequest<GithubResponsePage<T>>? = pagesRequest.initialRequest
    while (request != null) {
      val page = executor.execute(indicator, request)
      for (item in page.items) {
        result.add(item)
        if (result.size == maximum) return result
      }
      request = page.nextLink?.let(pagesRequest.urlRequestProvider)
    }
    return result
  }

  class Request<T>(val initialRequest: GithubApiRequest<GithubResponsePage<T>>,
                   val urlRequestProvider: (String) -> GithubApiRequest<GithubResponsePage<T>>)
}