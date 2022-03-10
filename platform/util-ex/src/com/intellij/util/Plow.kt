// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.containers.ContainerUtil

/**
 * A Processor + Flow, or the Poor man's Flow: a reactive-stream-like wrapper around [Processor].
 *
 * Instead of creating the [Processor] manually - create a [Plow] and work with the Processor inside a lambda.
 * If your method accepts the processor it is the good idea to return a [Plow] instead to avoid the "return value in parameter" semantic
 *
 * Itself [Plow] is stateless and ["Cold"](https://projectreactor.io/docs/core/3.3.9.RELEASE/reference/index.html#reactor.hotCold),
 * meaning that the same instance of [Plow] could be reused several times (by reevaluating the sources),
 * but depending on the idempotence of the [producingFunction] this contract could be violated,
 * so to be careful, and it is better to obtain the new [Plow] instance each time.
 */
class Plow<T> private constructor(private val producingFunction: (Processor<T>) -> Boolean) {

  @Suppress("UNCHECKED_CAST")
  fun processWith(processor: Processor<in T>): Boolean = producingFunction(processor as Processor<T>)

  fun <P : Processor<T>> processTo(processor: P): P = processor.apply { producingFunction(this) }

  fun findAny(): T? = processTo(CommonProcessors.FindFirstProcessor()).foundValue

  fun find(test: (T) -> Boolean): T? = processTo(object : CommonProcessors.FindFirstProcessor<T>() {
    override fun accept(t: T): Boolean = test(t)
  }).foundValue

  fun <C : MutableCollection<T>> collectTo(coll: C): C = coll.apply { processTo(CommonProcessors.CollectProcessor(this)) }

  fun toList(): List<T> = ContainerUtil.unmodifiableOrEmptyList(collectTo(SmartList()))

  fun toSet(): Set<T> = ContainerUtil.unmodifiableOrEmptySet(collectTo(HashSet()))

  fun toArray(array: Array<T>): Array<T> = processTo(CommonProcessors.CollectProcessor()).toArray(array)

  fun <R> transform(transformation: (Processor<R>) -> (Processor<T>)): Plow<R> =
    Plow { pr -> producingFunction(transformation(pr)) }

  fun <R> map(mapping: (T) -> R): Plow<R> = transform { pr -> Processor { v -> pr.process(mapping(v)) } }
  
  fun <R> mapNotNull(mapping: (T) -> R?): Plow<R> = transform { pr -> Processor { v -> mapping(v)?.let { pr.process(it) } ?: true } }

  fun filter(test: (T) -> Boolean): Plow<T> = transform { pr -> Processor { v -> !test(v) || pr.process(v) } }

  fun <R> mapToProcessor(mapping: (T, Processor<R>) -> Boolean): Plow<R> =
    Plow { rProcessor -> producingFunction(Processor { t -> mapping(t, rProcessor) }) }

  fun <R> flatMap(mapping: (T) -> Plow<R>): Plow<R> = mapToProcessor { t, processor -> mapping(t).processWith(processor) }

  fun cancellable(): Plow<T> = transform { pr -> Processor { v -> ProgressManager.checkCanceled();pr.process(v) } }

  fun limit(n: Int): Plow<T> {
    var processedCount = 0
    return transform { pr ->
      Processor {
        processedCount++
        processedCount <= n && pr.process(it)
      }
    }
  }

  companion object {

    @JvmStatic
    fun <T> empty(): Plow<T> = of { true }

    @JvmStatic
    fun <T> of(processorCall: (Processor<T>) -> Boolean): Plow<T> = Plow(processorCall)

    @JvmStatic
    @JvmName("ofArray")
    fun <T> Array<T>.toPlow(): Plow<T> = Plow { pr -> all { pr.process(it) } }

    @JvmStatic
    @JvmName("ofIterable")
    fun <T> Iterable<T>.toPlow(): Plow<T> = Plow { pr -> all { pr.process(it) } }  
    
    @JvmStatic
    @JvmName("ofSequence")
    fun <T> Sequence<T>.toPlow(): Plow<T> = Plow { pr -> all { pr.process(it) } }

    @JvmStatic
    fun <T> concat(vararg plows: Plow<T>): Plow<T> = of { pr -> plows.all { it.processWith(pr) } }
  }

}