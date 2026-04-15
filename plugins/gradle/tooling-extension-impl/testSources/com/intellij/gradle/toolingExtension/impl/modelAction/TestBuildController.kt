// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction

import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsOrdered
import com.intellij.testFramework.common.mock.notImplemented
import org.gradle.api.Action
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.FetchModelResult
import org.gradle.tooling.internal.consumer.DefaultFetchModelResult
import org.gradle.tooling.model.Model

internal class TestBuildController : BuildController by notImplemented(BuildController::class.java) {

  private val modelRequests = ArrayList<TestModelRequest>()
  private val runActionCounts = ArrayList<Int>()
  private val models = HashMap<Pair<Model?, Class<*>>, Any>()
  private val modelFailures = HashMap<Pair<Model?, Class<*>>, Exception>()
  private val parameterFactories = HashMap<Class<*>, (() -> Any)>()

  fun <M : Any> registerModels(modelType: Class<M>, models: Map<out Model, M>) {
    for ((target, model) in models) {
      registerModel(target, modelType, model)
    }
  }

  fun <M : Any> registerModel(target: Model, modelType: Class<M>, model: M) {
    models[target to modelType] = model
  }

  fun registerModelFailure(target: Model, modelType: Class<*>, failure: Exception) {
    modelFailures[target to modelType] = failure
  }

  fun <P : Any> registerParameter(parameterType: Class<P>, parameterFactory: () -> P) {
    parameterFactories[parameterType] = parameterFactory
  }

  fun assertModelRequests(expectedModelRequests: List<TestModelRequest>) {
    assertEqualsOrdered(expectedModelRequests, modelRequests)
  }

  fun assertRunActionCounts(expectedRunActionCounts: List<Int>) {
    assertEqualsOrdered(expectedRunActionCounts, runActionCounts)
  }

  override fun <T> findModel(target: Model?, modelType: Class<T>): T? {
    modelRequests.add(TestModelRequest(target!!, modelType))
    throwModelFailure(target, modelType)
    @Suppress("UNCHECKED_CAST")
    return models[target to modelType] as T?
  }

  override fun <T, P : Any> findModel(
    target: Model?,
    modelType: Class<T>,
    parameterType: Class<P>?,
    parameterInitializer: Action<in P>?,
  ): T? {
    val parameter = createParameter(parameterType!!, parameterInitializer!!)
    modelRequests.add(TestModelRequest(target!!, modelType, parameterType, parameter))
    throwModelFailure(target, modelType)
    @Suppress("UNCHECKED_CAST")
    return models[target to modelType] as T?
  }

  private fun throwModelFailure(target: Model?, modelType: Class<*>) {
    val failure = modelFailures[target to modelType]
    if (failure != null) {
      throw failure
    }
  }

  private fun <P : Any> createParameter(parameterType: Class<P>, parameterInitializer: Action<in P>): P {
    val parameterFactory = parameterFactories[parameterType] ?: return notImplemented(parameterType)
    val parameter = @Suppress("UNCHECKED_CAST") (parameterFactory() as P)
    parameterInitializer.execute(parameter)
    return parameter
  }

  override fun <T : Any> fetch(modelType: Class<T>): FetchModelResult<T> =
    fetchModelResult { findModel(modelType) }

  override fun <T : Any> fetch(target: Model, modelType: Class<T>): FetchModelResult<T> =
    fetchModelResult { findModel(target, modelType) }

  override fun <T : Any, P : Any> fetch(
    modelType: Class<T>,
    parameterType: Class<P>?,
    parameterInitializer: Action<in P>?,
  ): FetchModelResult<T> =
    fetchModelResult { findModel(modelType, parameterType, parameterInitializer) }

  override fun <T : Any, P : Any> fetch(
    target: Model?,
    modelType: Class<T>,
    parameterType: Class<P>?,
    parameterInitializer: Action<in P>?,
  ): FetchModelResult<T> =
    fetchModelResult { findModel(target, modelType, parameterType, parameterInitializer) }

  override fun <T> run(actions: Collection<BuildAction<out T>>): List<T> {
    runActionCounts.add(actions.size)
    return actions.map { it.execute(this) }
  }

  override fun send(value: Any?) = Unit

  private fun <T : Any> fetchModelResult(modelProvider: () -> T?): FetchModelResult<T> {
    return try {
      DefaultFetchModelResult.of(modelProvider(), emptyList())
    }
    catch (failure: Exception) {
      DefaultFetchModelResult.failure(failure)
    }
  }

  data class TestModelRequest(
    val target: Model,
    val modelClass: Class<*>,
    val parameterClass: Class<*>? = null,
    val parameter: Any? = null,
  )
}
