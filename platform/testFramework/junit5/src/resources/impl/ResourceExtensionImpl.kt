// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.resources.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.UserDataHolder
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.impl.TypedStoreKey
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.computeIfAbsentTyped
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.getTyped
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.putTyped
import com.intellij.testFramework.junit5.resources.FullApplication
import com.intellij.testFramework.junit5.resources.NoInject
import com.intellij.testFramework.junit5.resources.ResourceExtensionApi
import com.intellij.testFramework.junit5.resources.providers.ParameterizableResourceProvider
import com.intellij.testFramework.junit5.resources.providers.PathInfo.Companion.cleanUpPathInfo
import com.intellij.testFramework.junit5.resources.providers.ResourceProvider
import com.intellij.testFramework.junit5.resources.providers.ResourceStorage
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import kotlin.reflect.KClass

/**
 * The main implementation class that implements [Extension] and all required callbacks serving as a bridge between JUnit and [ResourceProvider].
 * According to [lifetime] rules it creates resources either [beforeEach] or [beforeAll], and at the end deletes all [instancesToDeleteKey]
 * User might create more resources with [ResourceExtensionApi] extension method.
 * Automatically created resource stored in [automaticInstanceKey] and can be obtained with [ResourceStorage] by providers.
 *
 * Users create its instance using [com.intellij.testFramework.junit5.resources.ResourceExtensionApi.forProvider] or using extensions that implement [ResourceExtension] by delegating to this class.
 */
@TestOnly
class ResourceExtensionImpl<R : Any, PR : ResourceProvider<R>>(private val provider: PR) : ResourceExtension<R, PR> {
  /**
   * Used for [createManually] since this method doesn't have context access.
   */
  @Volatile
  private lateinit var latestContext: ExtensionContext
  private val lifetime = ResourceLifetime()

  /**
   * No reason to have more than one instance of provider in context (like there is no reason to have two identical extensions), so we use
   * provider id as key
   */
  private val id = this.provider::class.let { it.qualifiedName ?: it.hashCode().toString() }

  /**
   * Instance created automatically and injected in arguments and fields.
   */
  private val automaticInstanceKey = TypedStoreKey(id, provider.resourceType)

  /**
   * All instances (both automatic and manually created) to be deleted
   */
  private val instancesToDeleteKey = TypedStoreKey.createList<R>("[$id")

  companion object {
    /**
     * Stored in context if at least one class-level resource used
     */
    private val testHasClassLifeTimeResources = TypedStoreKey("testHasClassLifeTimeResources", Boolean::class)

    private fun <R : Any> KClass<out ResourceProvider<out R>>.keyForResourceByProvider(type: KClass<R>): TypedStoreKey<R> =
      TypedStoreKey("resourceBy${java.name}", type)

    private val ExtensionContext.store: ExtensionContext.Store get() = getStore(ExtensionContext.Namespace.GLOBAL)!!

    @TestOnly
    internal suspend fun <R : Any, PR : ResourceProvider<R>> createManually(api: ResourceExtensionApi<R, PR>,
                                                                            disposeOnExit: Boolean): R =
      api.asImpl().createNew(automatic = false, deleteOnExit = disposeOnExit)

    @TestOnly
    internal suspend fun <R : Any, P, PR : ParameterizableResourceProvider<R, P>> createManually(api: ResourceExtensionApi<R, PR>,
                                                                                                 params: P,
                                                                                                 disposeOnExit: Boolean): R =
      api.asImpl().createNew(automatic = false, deleteOnExit = disposeOnExit) { provider, storage ->
        provider.create(storage, params)
      }

    @TestOnly
    private fun <R : Any, PR : ResourceProvider<R>> ResourceExtensionApi<R, PR>.asImpl(): ResourceExtensionImpl<R, PR> {
      return this as ResourceExtensionImpl<R, PR>
    }

    /**
     * True, if this test has at least one class-level resource.
     * That is checked by Application which can't assume disposables are freed after each test
     */
    fun testHasClassLifeTimeResources(context: ExtensionContext): Boolean = context.store.getTyped(testHasClassLifeTimeResources) == true
  }

  override fun beforeAll(context: ExtensionContext) {
    this.latestContext = context
    if (lifetime.classLevel) {
      context.store.putTyped(testHasClassLifeTimeResources, true)
      val clazz = context.element.get() as Class<*>
      fillFieldsWithResource(createNewAutomatically(context), clazz, clazz)
    }
  }

  override fun afterAll(context: ExtensionContext) {
    if (lifetime.classLevel) {
      destroyAutomatically(context)
    }
  }

  override fun beforeEach(context: ExtensionContext) {

    this.latestContext = context
    if (lifetime.methodLevel) {
      val instance = context.testInstance.get()
      fillFieldsWithResource(createNewAutomatically(context), instance.javaClass, instance)
    }
  }

  override fun afterEach(context: ExtensionContext) {
    if (lifetime.methodLevel) {
      destroyAutomatically(context)
    }
  }

  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
    (!parameterContext.annotatedElement.isAnnotationPresent(NoInject::class.java)) && parameterContext.parameter.type.isAssignableFrom(
      this.provider.resourceType.java)

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): R {
    assert(supportsParameter(parameterContext, extensionContext)) { "Method shouldn't be called" }
    return extensionContext.store.getTyped(automaticInstanceKey) ?: error("No ${this.provider.resourceType} in ${automaticInstanceKey}")
  }

  @RequiresBlockingContext
  private fun createNewAutomatically(context: ExtensionContext): R = timeoutRunBlocking {
    createNew(context, automatic = true, deleteOnExit = true)
  }

  @RequiresBlockingContext
  private fun destroyAutomatically(context: ExtensionContext): Unit = timeoutRunBlocking {
    destroy(context)
  }

  private fun fillFieldsWithResource(value: R, clazz: Class<*>, instance: Any) {
    for (field in clazz.declaredFields.filter { it.type.isAssignableFrom(provider.resourceType.java) }) {
      if (field.isAnnotationPresent(NoInject::class.java)) {
        continue
      }
      field.isAccessible = true
      if (field.get(instance) == null) {
        field.set(instance, value)
      }
    }
  }

  /**
   * Creates new resource with aid of [provider].
   * [automatic] resources are created on [beforeEach] or [beforeAll] (not by user called [create]).
   * Resources with [deleteOnExit] are [destroy]ed at the end of [lifetime]
   * [create] is a callback to create [R] differs between [ResourceProvider] and [ParameterizableResourceProvider]
   */
  @TestOnly
  private suspend fun createNew(context: ExtensionContext = latestContext,
                                automatic: Boolean,
                                deleteOnExit: Boolean,
                                create: suspend (PR, ResourceStorage) -> R = { pr, st -> pr.create(st) }): R {
    if (provider.needsApplication && ApplicationManager.getApplication() == null) {
      throw IllegalStateException("""
        ${this::class} can't produce ${provider.resourceType} in absence of Application.
        Consider using ${TestApplication::class} or ${FullApplication::class} before this extension
      """.trimIndent())
    }
    val store = context.store
    val resourceStorage = object : ResourceStorage {
      @TestOnly
      override fun <R : Any, PR : ResourceProvider<R>> getResourceCreatedByProvider(provider: KClass<out PR>,
                                                                                    resourceType: KClass<R>): Result<R> {
        val funToCreate: (ResourceProvider<Any>) -> ResourceExtensionApi<Any, ResourceProvider<Any>> = ResourceExtensionApi.Companion::forProvider
        return store.getTyped(provider.keyForResourceByProvider(resourceType))?.let { Result.success(it) }
               ?: Result.failure(IllegalStateException(""" 
                 Instance of ${resourceType} requested by some extension. 
                  This instance expected to be created by provider $provider, 
                  but provider hasn't been installed using ${ResourceExtensionApi::class}. 
                  Use ${FullApplication::class} annotation, or register $provider manually 
                  using appropriate annotation or $funToCreate creation method """.trimIndent()))
      }

    }
    return create(this.provider, resourceStorage).also { resource ->
      if (deleteOnExit) {
        instancesToDelete(context).add(resource)
      }
      if (automatic) {
        store.apply {
          putTyped(automaticInstanceKey, resource)
          putTyped(provider::class.keyForResourceByProvider(provider.resourceType), resource)
        }
      }
    }
  }

  /**
   * Destroys resource. Resources with [com.intellij.testFramework.junit5.resources.providers.PathInfo]
   * are cleaned up with [UserDataHolder] extension method
   */
  @TestOnly
  private suspend fun destroy(context: ExtensionContext) {
    val resources = context.store.getTyped(instancesToDeleteKey) ?: return

    for (resource in resources) {
      assert(this.provider.resourceType.java.isAssignableFrom(resource.javaClass)) { "$resource shouldn't be here" }
      this.provider.destroy(resource)
      (resource as? UserDataHolder)?.cleanUpPathInfo()
      context.store.apply {
        if (get(provider::class.keyForResourceByProvider(provider.resourceType)) == resource) {
          remove(provider::class.keyForResourceByProvider(provider.resourceType), provider.resourceType.java)
        }
      }
    }
  }

  private fun instancesToDelete(context: ExtensionContext): MutableList<R> = context.store.computeIfAbsentTyped(instancesToDeleteKey) {
    ArrayList()
  }
}
