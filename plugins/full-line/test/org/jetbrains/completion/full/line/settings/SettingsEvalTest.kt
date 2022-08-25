package org.jetbrains.completion.full.line.settings

//import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import com.google.gson.Gson
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.AssertionFailedError
import org.jetbrains.completion.full.line.language.CheckboxFlag
import org.jetbrains.completion.full.line.language.IgnoreInInference
import org.jetbrains.completion.full.line.language.LangState
import org.jetbrains.completion.full.line.language.ModelState
import org.jetbrains.completion.full.line.models.ModelType
import org.jetbrains.completion.full.line.settings.state.GeneralState
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings
import org.jetbrains.completion.full.line.settings.state.MlServerCompletionAuthState
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.MockedStatic
import org.mockito.Mockito
import java.util.*
import kotlin.NoSuchElementException
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf

class SettingsEvalTest : BasePlatformTestCase() {
  private lateinit var system: MockedStatic<System>
  override fun setUp() {
    super.setUp()
    settings = MLServerCompletionSettings.getInstance().state.deepCopy()
    system = Mockito.mockStatic(System::class.java)
    setUpEnvs("flcc_evaluating" to "true")
  }

  override fun tearDown() {
    super.tearDown()
    setUpEnvs("flcc_evaluating" to "false")
    //Mockito.unmockStatic(System::class)
    MLServerCompletionSettings.getInstance().loadState(settings)
    Registry.getAll()
      .filter { it.pluginId == "org.jetbrains.completion.full.line" }
      .forEach { it.resetToDefault() }
  }

  fun `test GeneralState`() {
    checkClassEnvSupport<GeneralState> {
      MLServerCompletionSettings.getInstance().state
    }
  }

  fun `test LangState`() {
    MLServerCompletionSettings.availableLanguages.forEach { lang ->
      withSetUpEnvs("flcc_language" to lang.displayName.lowercase(Locale.getDefault())) {
        checkClassEnvSupport<LangState> {
          MLServerCompletionSettings.getInstance().state.langStates.getValue(lang.id)
        }
      }
    }
  }

  fun `test ModelState`() {
    MLServerCompletionSettings.availableLanguages.forEach { lang ->
      val langState = MLServerCompletionSettings.getInstance().state.langStates.getValue(lang.id)
      ModelType.values().forEach { type ->
        withSetUpEnvs(
          "flcc_language" to lang.displayName.toLowerCase(),
          "flcc_modelType" to type,
        ) {
          checkClassEnvSupport<ModelState> {
            MLServerCompletionSettings.getInstance().getModelState(langState, type)
          }
        }
      }
    }
  }

  @Suppress("UnresolvedPluginConfigReference")
  fun `test registries`() {
    MLServerCompletionSettings.availableLanguages.forEach { lang ->
      withSetUpEnvs(
        "flcc_host" to "0.0.0.0",
        "flcc_language" to lang.displayName.toLowerCase(),
      ) {
        assertNotEquals(Registry.get("full.line.server.host.${lang.displayName.toLowerCase()}").asString(), "0.0.0.0")
        MLServerCompletionSettings.getInstance().initializeComponent()
        assertEquals(Registry.get("full.line.server.host.${lang.displayName.toLowerCase()}").asString(), "0.0.0.0")
      }
    }
    withSetUpEnvs("flcc_max_latency" to 999) {
      assertNotEquals(Registry.get("full.line.server.host.max.latency").asInteger(), 999)
      MLServerCompletionSettings.getInstance().initializeComponent()
      assertEquals(Registry.get("full.line.server.host.max.latency").asInteger(), 999)
    }
    withSetUpEnvs("flcc_only_proposals" to true) {
      assertNotEquals(Registry.get("full.line.only.proposals").asBoolean(), true)
      MLServerCompletionSettings.getInstance().initializeComponent()
      assertEquals(Registry.get("full.line.only.proposals").asBoolean(), true)
    }
  }

  fun `test auth token`() {
    val token = "123123"

    assertNotEquals(MlServerCompletionAuthState.getInstance().state.authToken, token)
    withSetUpEnvs("flcc_token" to token) {
      MlServerCompletionAuthState.getInstance().initializeComponent()
      assertEquals(MlServerCompletionAuthState.getInstance().state.authToken, token)
    }
  }

  ///**
  // * Check if class `T` can be configured via environment variables
  // * @param getState function to get part of settings state to look for config variables and checks
  // */
  private inline fun <reified T : Any> checkClassEnvSupport(crossinline getState: () -> Any) {
    // Skip elements with `IgnoreInInference` annotation
    val keys = T::class.declaredMemberProperties
      .filterNot { it.hasAnnotation<IgnoreInInference>() }

    // Check that all elements can be modified with an environment variable
    keys.filterNot { it.hasAnnotation<CheckboxFlag>() }.forEach { prop ->
      withSetUpEnvs("flcc_${prop.name}" to createObj(getState(), prop)) {
        val was = prop.call(getState())
        MLServerCompletionSettings.getInstance().initializeComponent()
        assertNotEquals(was, prop.call(getState()), "For property `${prop.name}`.")
      }
    }

    // Check that props with `CheckboxFlag` annotation are always true
    keys.filter { it.hasAnnotation<CheckboxFlag>() }.forEach { prop ->
      assertEquals(prop.returnType.classifier, Boolean::class)
      if (prop !is KMutableProperty<*>)
        throw AssertionError("CheckboxFlag property ${prop.name} is not mutable")

      val generalState = getState()
      val name = prop.name.removePrefix("use").decapitalize()
      val realProp = T::class.declaredMemberProperties.find { it.name == name }
                     ?: throw NoSuchElementException("Missing $name property for CheckboxFlag ${prop.name} ")

      prop.setter.call(generalState, true)
      withSetUpEnvs("flcc_${name}" to createObj(generalState, realProp)) {
        MLServerCompletionSettings.getInstance().initializeComponent()
        assertEquals(prop.call(generalState) as Boolean?, true, "For CheckboxFlag property `${prop.name}`.")
      }
      prop.setter.call(generalState, false)
      withSetUpEnvs("flcc_${name}" to createObj(generalState, realProp)) {
        MLServerCompletionSettings.getInstance().initializeComponent()
        assertEquals(prop.call(generalState) as Boolean?, true, "For CheckboxFlag property `${prop.name}`.")
      }
    }
  }

  private fun createObj(instance: Any, prop: KProperty<*>): Any {
    val returnType = instance::class.declaredMemberProperties.find { it.name == prop.name }?.returnType
                     ?: throw AssertionFailedError("Class ${instance::class.qualifiedName} does not contains required ${prop.name} field")
    val cls = returnType.classifier
    if (cls !is KClass<*>) throw AssertionFailedError("classifier must be class")

    // get next enum element
    if (cls.isSubclassOf(Enum::class)) {
      return (prop.call(instance)!! as Enum<*>).next()
    }

    // get any element or return empty for collections
    if (cls.isSubclassOf(Iterable::class)) {
      val container = (prop.call(instance)!! as MutableCollection<*>)

      return if (container.isEmpty()) {
        val elementCls = (returnType.arguments[0].type!!.classifier as KClass<*>)
        when {
          elementCls.isSubclassOf(Enum::class) -> elementCls.java.enumConstants.first()
          elementCls.isSubclassOf(Boolean::class) -> "true"
          else -> "0"
        }
      }
      else {
        ""
      }
    }

    // handle objects
    return when (cls) {
      Boolean::class -> !(prop.call(instance) as Boolean)
      Int::class -> (prop.call(instance) as Int) + 1
      Float::class -> (prop.call(instance) as Float) + 1
      Double::class -> (prop.call(instance) as Double) + 1
      Char::class -> (prop.call(instance) as Char) + 1
      else -> throw IllegalArgumentException("Passed unsupported class `${cls.qualifiedName}`.")
    }
  }

  fun Enum<*>.next(): Enum<*> {
    val values = declaringClass.enumConstants
    val nextOrdinal = (ordinal + 1) % values.size
    return values[nextOrdinal]
  }

  private fun setUpEnvs(vararg envs: Pair<String, Any?>) {
    envs.forEach { (k, v) ->
      system.`when`<Any> { System.getenv(k) }
        .thenReturn(v?.toString())
    }
  }

  private fun withSetUpEnvs(vararg envs: Pair<String, Any?>, block: () -> Unit) {
    try {
      setUpEnvs(*envs)
      block()
    }
    finally {
      setUpEnvs(*envs.map { it.first to null }.toTypedArray())
    }
  }

  companion object {
    // restore settings after test case
    lateinit var settings: GeneralState


    fun <T : Any> T.deepCopy(): T = Gson().fromJson(Gson().toJson(this), this::class.java)
  }

  // Strict copy of KAnnotatedElement::hasAnnotation to support outdated 202 IDEA with previous kotlin version
  private inline fun <reified T : Annotation> KAnnotatedElement.hasAnnotation(): Boolean {
    return annotations.filterIsInstance<T>().firstOrNull() != null
  }
}
