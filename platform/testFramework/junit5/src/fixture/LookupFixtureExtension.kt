// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitAll
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.*
import org.junit.jupiter.api.extension.ExtensionContext.Namespace
import org.junit.platform.commons.support.HierarchyTraversalMode
import org.junit.platform.commons.support.ReflectionSupport
import java.lang.reflect.Modifier
import java.util.function.Predicate
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

/**
 * In case of multiple fixtures of the same type, this annotation can be used to specify the lookup name of the fixture.
 * Declaration on a field with fixture - marks the last one as the default fixture of its type.
 * Declaration on a value - specifies the usage of reference fixture (by field name).
 *
 * @property value fixture lookup name, the field name is used in case of an empty value.
 */
@TestOnly
@ApiStatus.Internal
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class LookupField(
  val value: String = "",
)

/**
 * Represents an explicit fixture defined in the class.
 *
 * @property lookupName The lookup name of the fixture (the field name).
 * @property implementation The test fixture implementation associated with the lookup.
 * @property isLookupDefault Indicates whether this lookup is the default (marked as default in case of multiple fixtures of the same type).
 */
@TestOnly
@ApiStatus.Internal
data class LookupFixture(val lookupName: String, val implementation: TestFixture<*>, val isLookupDefault: Boolean)

/**
 * Lookup fixtures might be declared as fields in test classes or instantiated with some default values implicitly.
 * This class manages them via the [ExtensionContext].
 */
@TestOnly
@ApiStatus.Internal
class LookupFixtureManager {
  private val lookupFixtures = mutableListOf<LookupFixture>()

  internal fun addLookupFixtures(fixtures: List<LookupFixture>) {
    lookupFixtures.addAll(fixtures)
  }

  fun <T> findInstance(requiredType: Class<T>, lookupName: String?): TestFixture<T>? {

    val compatibleFixtures = lookupFixtures.filter {
      it.implementation.get()?.javaClass?.let { requiredType.isAssignableFrom(it) } == true
    }

    val fixture = when {
      lookupName?.isNotEmpty() == true -> {
        compatibleFixtures.find { it.lookupName == lookupName }
        ?: error("Fixture with lookup name ${lookupName} is not found")
      }
      compatibleFixtures.isEmpty() -> null
      compatibleFixtures.size == 1 -> compatibleFixtures.first()
      else -> {
        compatibleFixtures.singleOrNull { it.isLookupDefault }
        ?: error("Single default fixture is not found, multiple choices: ${compatibleFixtures.joinToString(", ") { it.lookupName }}")
      }
    }

    return fixture?.let { it.implementation as TestFixture<T> }
  }

  inline fun <reified T> getOrDefault(builder: () -> TestFixture<T>): TestFixture<T> {
    val explicit = this.findInstance(T::class.java, null)
    return explicit ?: builder.invoke()
  }

  inline fun <reified T> getRequired(): TestFixture<T> {
    val explicit = this.findInstance(T::class.java, null)
    return explicit ?: error("Can't resolve ${T::class.java} fixture")
  }

  private fun getFieldDeclaredLookupFixtures(context: ExtensionContext, static: Boolean): List<LookupFixture> {
    val testClass: Class<*> = context.testClass.getOrNull() ?: error("Doesn't work for empty test class")
    val testInstance = context.testInstance.getOrNull()

    val fields = ReflectionSupport.findFields(testClass, Predicate { field ->
      TestFixture::class.java.isAssignableFrom(field.type) && Modifier.isStatic(field.modifiers) == static
    }, HierarchyTraversalMode.TOP_DOWN)

    val explicitFixtures = fields.map { field ->
      val isLookupDefault = field.isAnnotationPresent(LookupField::class.java)
      val lookupName = field.name
      val fixture = run {
        field.isAccessible = true
        field.get(testInstance) as TestFixture<*>
      }
      LookupFixture(lookupName, fixture, isLookupDefault)
    }

    return explicitFixtures
  }

  fun enrichWithFieldDeclaredLookupFixtures(context: ExtensionContext, static: Boolean) {
    val explicitFixtures = getFieldDeclaredLookupFixtures(context, static)
    addLookupFixtures(explicitFixtures)
  }
}

/**
 * Allows registering implicit fixtures,
 * fixtures that are not declared as fields in the test class but instantiated implicitly with default values.
 * (like project, module, source root, psiFile, editor, etc)
 *
 * Handles [LookupFixtureManager] lifecycle.
 */
@TestOnly
@ApiStatus.Internal
class LookupFixtureExtension : BeforeAllCallback, BeforeEachCallback, AfterAllCallback, AfterEachCallback, Extension, ParameterResolver {
  companion object {
    fun ExtensionContext.getLookupFixtureManager(): LookupFixtureManager {
      val store = getStore(Namespace.GLOBAL)
      return store.get(LookupFixtureManager::class.java, LookupFixtureManager::class.java)
             ?: error("LookupFixtureManager is not created")
    }

    private fun ExtensionContext.createLookupFixtureManager(static: Boolean): LookupFixtureManager {
      val manager = LookupFixtureManager().also {
        val store = getStore(Namespace.GLOBAL)
        store.put(LookupFixtureManager::class.java, it)
      }

      manager.enrichWithFieldDeclaredLookupFixtures(this, static)

      return manager
    }

    private fun ExtensionContext.removeLookupFixtureManager() {
      val store = getStore(Namespace.GLOBAL)
      store.remove(LookupFixtureManager::class.java)
    }

    suspend fun ExtensionContext.registerImplicitFixtures(implicitFixtures: List<LookupFixture>) {
      val scopeStore = getStore(Namespace.GLOBAL)
      val testScope = scopeStore.get("TestFixtureExtension") as CoroutineScope
      val pendingFixtures = implicitFixtures.map { (it.implementation as TestFixtureImpl<*>).init(testScope, TestContextImpl(this, null)) }

      pendingFixtures.awaitAll()

      getLookupFixtureManager().addLookupFixtures(implicitFixtures)
    }
  }

  override fun beforeAll(context: ExtensionContext) {
    context.createLookupFixtureManager(static = true)
  }

  override fun beforeEach(context: ExtensionContext) {
    context.createLookupFixtureManager(static = false)
  }

  override fun afterAll(context: ExtensionContext) {
    context.removeLookupFixtureManager()
  }

  override fun afterEach(context: ExtensionContext) {
    context.removeLookupFixtureManager()
  }

  private fun resolve(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any? {
    val lookupFieldAnnotation = parameterContext.findAnnotation(LookupField::class.java)
    val lookupName = lookupFieldAnnotation.map { it.value }.getOrDefault("").takeIf { it.isNotEmpty() }

    val requiredType = parameterContext.parameter.type
    val manager = extensionContext.getLookupFixtureManager()
    val fixture = manager.findInstance(requiredType, lookupName)

    return fixture?.get()
  }

  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
    return resolve(parameterContext, extensionContext) != null
  }


  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
    return resolve(parameterContext, extensionContext)
           ?: error("Not supported parameter received ${parameterContext.parameter.type}")
  }
}
