// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.modelAction

import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertion
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertion.Companion.assertCollectionOrdered
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEmpty
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsOrdered
import com.intellij.testFramework.common.mock.notImplemented
import org.gradle.api.Action
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.FetchModelResult
import org.gradle.tooling.UnknownModelException
import org.gradle.tooling.internal.consumer.DefaultFetchModelResult
import org.gradle.tooling.model.Model
import java.io.File

internal class TestBuildController : BuildController by notImplemented(BuildController::class.java) {

  private val modelRequests = ArrayList<TestModelRequest<*>>()
  private val runActionCounts = ArrayList<Int>()
  private val sentValues = ArrayList<Any>()
  private val models = HashMap<Pair<Model?, Class<*>>, Any>()
  private val modelFailures = HashMap<Pair<Model?, Class<*>>, Exception>()

  fun <M : Any> registerModels(modelType: Class<M>, models: List<Pair<Model, M>>) {
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

  fun assertModelRequests(expectedModelRequests: List<TestModelRequest<*>>) {
    assertEqualsOrdered(expectedModelRequests, modelRequests)
  }

  fun assertModelRequests(vararg expectedModelRequests: TestModelRequest<*>) {
    assertModelRequests(expectedModelRequests.asList())
  }

  fun assertRunActions(vararg expectedRunActionCounts: Int) {
    assertEqualsOrdered(expectedRunActionCounts.asList(), runActionCounts)
  }

  fun assertNoRunActions() {
    assertEmpty(runActionCounts)
  }

  fun assertSentFailureTargetPaths(vararg expectedTargetPaths: File) {
    val actualTargetPaths = sentValues
      .filterIsInstance<GradleModelFetchFailureState>()
      .map { it.failureResult.targetPath }
    assertEqualsOrdered(expectedTargetPaths.asList(), actualTargetPaths)
  }

  fun assertSentFailureExceptions(configure: CollectionAssertion<GradleModelFetchFailure>.() -> Unit) {
    val actualFailures = sentValues
      .filterIsInstance<GradleModelFetchFailureState>()
      .flatMap { it.failureResult.failures }
    assertCollectionOrdered(actualFailures, configure)
  }

  fun assertNoSentFailures() {
    val actualFailures = sentValues
      .filterIsInstance<GradleModelFetchFailureState>()
      .flatMap { it.failureResult.failures }
    assertEmpty(actualFailures)
  }

  override fun <T> getModel(target: Model, modelType: Class<T>): T {
    return findModel(target, modelType) ?: throwUnknownModelException(modelType)
  }

  override fun <T, P : Any> getModel(
    target: Model,
    modelType: Class<T>,
    parameterType: Class<P>,
    parameterInitializer: Action<in P>,
  ): T {
    return findModel(target, modelType, parameterType, parameterInitializer) ?: throwUnknownModelException(modelType)
  }

  override fun <T> findModel(
    target: Model,
    modelType: Class<T>,
  ): T? {
    return findModel(TestModelRequest(target, modelType))
  }

  override fun <T, P : Any> findModel(
    target: Model,
    modelType: Class<T>,
    parameterType: Class<P>,
    parameterInitializer: Action<in P>,
  ): T? {
    return findModel(TestModelRequest(target, modelType, parameterType, parameterInitializer))
  }

  private fun <T> findModel(request: TestModelRequest<T>): T? {
    modelRequests.add(request)
    val modelKey = request.target to request.modelType
    val failure = modelFailures[modelKey]
    if (failure != null) {
      throw failure
    }
    val model = models[modelKey]
    if (model != null) {
      return request.modelType.cast(model)
    }
    return null
  }

  override fun <T : Any> fetch(target: Model, modelType: Class<T>): FetchModelResult<T> {
    return fetchModelResult { findModel(target, modelType) ?: throwUnknownModelException(modelType) }
  }

  override fun <T : Any, P : Any> fetch(
    target: Model?,
    modelType: Class<T>,
    parameterType: Class<P>?,
    parameterInitializer: Action<in P>?,
  ): FetchModelResult<T> =
    fetchModelResult { findModel(target!!, modelType, parameterType!!, parameterInitializer!!) }

  override fun <T> run(actions: Collection<BuildAction<out T>>): List<T> {
    runActionCounts.add(actions.size)
    return actions.map { it.execute(this) }
  }

  override fun send(value: Any?) {
    if (value != null) sentValues.add(value)
  }

  private fun throwUnknownModelException(modelType: Class<*>): Nothing {
    throw UnknownModelException("No builders are available to build a model of type '${modelType.name}'.")
  }

  private fun <T : Any> fetchModelResult(modelProvider: () -> T?): FetchModelResult<T> {
    return try {
      DefaultFetchModelResult.of(modelProvider(), emptyList())
    }
    catch (failure: Exception) {
      DefaultFetchModelResult.failure(failure)
    }
  }

  data class TestModelRequest<T>(
    val target: Model,
    val modelType: Class<T>,
    val parameterType: Class<*>? = null,
    val parameterInitializer: Action<*>? = null,
  )
}
