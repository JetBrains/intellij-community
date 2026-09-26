// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.psi.versioning

import com.intellij.platform.testFramework.junit5.codeInsight.psi.LightweightCommitScenario
import com.intellij.platform.testFramework.junit5.codeInsight.psi.assertLightweightCommitScenario
import com.intellij.platform.testFramework.junit5.codeInsight.psi.replaceBetween
import com.intellij.platform.testFramework.junit5.codeInsight.psi.runVersionedTest
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * AI-generated tests.
 *
 * Checks the behavior of lightweight commit across various Kotlin language structures.
 */
@TestApplication
internal class KotlinLightweightDocumentCommitTest {

  private companion object {
    private const val RAW_QUOTES_PLACEHOLDER = "__RAW_QUOTES__"

    @Language("kotlin")
    private val BASE_TEXT = $$"""
      package versioning.stress

      import kotlin.math.absoluteValue
      import kotlin.reflect.KClass

      /**
       * Stress fixture for lightweight document commit.
       *
       * The source intentionally mixes Kotlin language constructs which produce different Kotlin PSI nodes:
       * declarations, KDoc, annotations, sealed hierarchies, enums, objects, generics with variance,
       * inline reified functions, extension functions, lambdas, string templates and raw strings.
       *
       * @param T value type used by the iterable contract
       * @see FeatureStress.describe
       */
      @Target(
        AnnotationTarget.CLASS,
        AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY,
        AnnotationTarget.VALUE_PARAMETER,
      )
      @Retention(AnnotationRetention.RUNTIME)
      annotation class Marker(val value: String, val numbers: IntArray = [])

      typealias Renderer<T> = (T) -> String

      sealed interface Shape {
        fun name(): String
      }

      data class Circle(@Marker("radius") val radius: Double) : Shape {
        override fun name(): String = "circle:$radius"
      }

      data class Rectangle(val width: Double, val height: Double) : Shape {
        override fun name(): String = "rectangle:${width}x${height}"
      }

      class Group(shapes: List<out Shape>) : Shape {
        private val shapes: List<Shape> = shapes.toList()

        override fun name(): String = shapes.joinToString(separator = "+") { it.name() }
      }

      @Marker("fixture", numbers = [1, 2, 3])
      class FeatureStress<T : Comparable<T>>(@Marker("value") val value: T) : Iterable<T> {
        val names: MutableList<String> = mutableListOf()

        private val template: String = __RAW_QUOTES__
          line one
            line two with "quotes"
          line three
          __RAW_QUOTES__.trimIndent()

        private val lazyLabel: String by lazy { "lazy:" + value.toString() }

        constructor(value: T, extraName: String) : this(value) {
          names.add(extraName)
        }

        init {
          names.add(template)
        }

        // MEMBERS_START

        /**
         * Describes an arbitrary input with a `when` expression.
         *
         * @param input anything accepted by the fixture
         * @return a stable textual representation
         */
        fun describe(input: Any?): String {
          // DESCRIBE_BODY_START
          return when {
            input == null -> "null"
            input is String && input.isNotBlank() -> input.trim()
            input is Int -> "integer:${input.absoluteValue}"
            input is Shape -> input.name()
            input is List<*> && input.isNotEmpty() -> summarize(input)
            else -> input.toString()
          }
          // DESCRIBE_BODY_END
        }

        /** Combines lambdas, an object expression, and a local class. */
        fun compute(values: List<T>, renderer: Renderer<T>): String {
          val callback = object : Runnable {
            override fun run() {
              names.add("anonymous")
            }
          }
          callback.run()

          class LocalRenderer(private val prefix: String) {
            fun render(): String = values.joinToString(separator = ",", prefix = prefix, transform = renderer)
          }

          return LocalRenderer("local:").render()
        }

        inline fun <reified R : Any> describeType(kind: KClass<R>): String {
          val (left, right) = kind.simpleName.orEmpty() to R::class.simpleName.orEmpty()
          return "$left/$right"
        }

        fun risky(mode: Mode): String {
          try {
            return sequence {
              yield(mode.label())
              yieldAll(names)
            }.joinToString("|")
          }
          catch (ex: IllegalStateException) {
            throw ex
          }
          finally {
            synchronized(names) {
              names.add("closed")
            }
          }
        }

        private fun summarize(values: Collection<*>): String = values.joinToString(separator = "|") { it.toString() }

        override fun iterator(): Iterator<T> = listOf(value).iterator()

        enum class Mode(private val label: String) {
          FAST("fast"),
          // LEGACY_CONSTANTS_START
          SAFE("safe"),
          LEGACY("legacy");
          // LEGACY_CONSTANTS_END

          fun label(): String = label.uppercase()
        }

        // LEGACY_ADAPTER_START
        internal class LegacyAdapter {
          fun adapt(key: String, vararg values: String): Map<String, List<String>> = linkedMapOf(key to values.toList())
        }
        // LEGACY_ADAPTER_END

        object Registry {
          val known: MutableSet<String> = linkedSetOf(DEFAULT_NAME)
        }

        companion object {
          const val DEFAULT_NAME: String = "default"

          val defaults: Map<String, List<Number>> = mapOf(
            "integers" to listOf(1, 2, 3),
            "mixed" to listOf(1, 2L, 3.0),
          )
        }

        // MEMBERS_END
      }
    """.trimIndent().replace(RAW_QUOTES_PLACEHOLDER, "\"\"\"")

    private val REPLACED_DESCRIBE_BODY = $$"""
          val computed = when (input) {
            null -> "replacement:null"
            is Mode -> "mode:${input.label()}"
            is Shape -> if (input.name().contains(":")) "shape:${input.name()}" else input.name()
            is Collection<*> -> summarize(input)
            else -> input.toString()
          }
          return computed
    """.trimIndent().prependIndent("  ") + "\n"

    private val INSERTED_MEMBER = """
        /**
         * Added by the lightweight commit test to cover generic members with an upper bound.
         *
         * @param sink accepts the same value through an out projected list
         * @param value value to store and return
         * @return the value visible only in the lightweight committed PSI branch
         */
        @Marker("inserted")
        fun <R : CharSequence> insertedFeature(sink: MutableList<in R>, value: R): R? {
          sink.add(value)
          return value.takeIf { it.isNotEmpty() }
        }

    """.trimIndent().prependIndent("  ")

    private fun replaceDescribeBody(): String = BASE_TEXT.replaceBetween(
      "    // DESCRIBE_BODY_START\n",
      "    // DESCRIBE_BODY_END",
      REPLACED_DESCRIBE_BODY,
    )

    private fun insertMember(text: String): String = text.replace(
      "  // MEMBERS_END",
      "${INSERTED_MEMBER}  // MEMBERS_END",
    )

    private fun removeLegacyDeclarations(text: String): String = text
      .replaceBetween(
        "    // LEGACY_CONSTANTS_START\n",
        "    // LEGACY_CONSTANTS_END",
        "    SAFE(\"safe\");\n",
      )
      .replaceBetween(
        "  // LEGACY_ADAPTER_START\n",
        "  // LEGACY_ADAPTER_END",
        "",
      )

    private fun KtFile.mainClass(): KtClass = declarations.filterIsInstance<KtClass>().single { it.name == "FeatureStress" }

    private fun KtClass.declaration(name: String) = body?.declarations?.singleOrNull { it.name == name }

    private fun KtClass.function(name: String): KtNamedFunction? =
      body?.declarations?.filterIsInstance<KtNamedFunction>()?.singleOrNull { it.name == name }

    private val replaceBodyScenario = kotlinScenario(
      name = "replace complex when method body",
      updatedText = replaceDescribeBody(),
      assertMainClass = { mainClass ->
        val describe = mainClass.function("describe")!!
        Assertions.assertTrue(describe.text.contains("is Mode ->"), describe.text)
        Assertions.assertTrue(describe.text.contains("return computed"), describe.text)
        Assertions.assertNotNull(describe.bodyExpression, "Replaced function body must be reparsed")
      },
    )

    private val insertMemberScenario = kotlinScenario(
      name = "insert kdoc annotation and generic member",
      updatedText = insertMember(BASE_TEXT),
      assertMainClass = { mainClass ->
        val inserted = mainClass.function("insertedFeature")
        Assertions.assertNotNull(inserted, "Inserted generic function should be available in lightweight committed PSI")
        Assertions.assertTrue(inserted!!.docComment!!.text.contains("out projected list"))
        Assertions.assertEquals("R", inserted.typeParameters.single().name)
        Assertions.assertEquals(listOf("Marker"), inserted.annotationEntries.map { it.shortName?.asString() })
      },
    )

    private val removeDeclarationsScenario = kotlinScenario(
      name = "remove enum entry and nested declaration",
      updatedText = removeLegacyDeclarations(BASE_TEXT),
      assertMainClass = { mainClass ->
        val mode = mainClass.declaration("Mode") as KtClass
        val entries = mode.body?.enumEntries.orEmpty().map { it.name }
        Assertions.assertEquals(listOf("FAST", "SAFE"), entries, mode.text)
        Assertions.assertNull(mainClass.declaration("LegacyAdapter"))
      },
    )

    private val wideUpdateScenario = kotlinScenario(
      name = "wide update with replacements insertions and removals",
      updatedText = removeLegacyDeclarations(insertMember(replaceDescribeBody())),
      assertMainClass = { mainClass ->
        Assertions.assertNotNull(mainClass.function("insertedFeature"))
        Assertions.assertTrue(mainClass.function("describe")!!.text.contains("is Collection<*> ->"))
        Assertions.assertNull(mainClass.declaration("LegacyAdapter"))
      },
    )

    private fun kotlinScenario(
      name: String,
      updatedText: String,
      assertMainClass: (KtClass) -> Unit,
    ): LightweightCommitScenario<KtFile> = LightweightCommitScenario(name, updatedText) { ktFile ->
      assertMainClass(ktFile.mainClass())
    }

    private fun assertKotlinShape(ktFile: KtFile) {
      Assertions.assertEquals("versioning.stress", ktFile.packageDirective?.fqName?.asString())
      Assertions.assertEquals(
        listOf("kotlin.math.absoluteValue", "kotlin.reflect.KClass"),
        ktFile.importDirectives.map { it.importedFqName?.asString() },
      )
      Assertions.assertEquals(
        listOf("Marker", "Renderer", "Shape", "Circle", "Rectangle", "Group", "FeatureStress"),
        ktFile.declarations.map { it.name },
      )
      Assertions.assertNotNull(ktFile.declarations.filterIsInstance<KtTypeAlias>().singleOrNull { it.name == "Renderer" })

      val mainClass = ktFile.mainClass()
      Assertions.assertNotNull(mainClass.declaration("names") as? KtProperty)
      Assertions.assertNotNull(mainClass.function("describe"))
      Assertions.assertNotNull(mainClass.declaration("Mode"))
      Assertions.assertNotNull(mainClass.declaration("Registry"))
      Assertions.assertEquals(1, mainClass.companionObjects.size)
      Assertions.assertEquals(1, mainClass.secondaryConstructors.size)
      Assertions.assertTrue(PsiTreeUtil.findChildrenOfType(ktFile, KDoc::class.java).size >= 3)
    }
  }

  private val _project = projectFixture(openAfterCreation = true)
  private val _module = _project.moduleFixture("src")
  private val _sourceRoot = _module.sourceRootFixture()
  private val _psiFile = _sourceRoot.psiFileFixture("FeatureStress.kt", "package versioning.stress\n")
  private val _editor = _psiFile.editorFixture()

  private val project by _project
  private val editor by _editor

  @BeforeEach
  fun awaitIndexing() {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  /**
   * Every scenario gets its own project, so that a regression in repeated commits on one document is reported by
   * [lightweight commit can be repeated on the same document] alone instead of by whichever scenario runs at that point.
   */
  private fun runScenario(scenario: LightweightCommitScenario<KtFile>) {
    runVersionedTest(project) {
      assertLightweightCommitScenario(project, editor.document, BASE_TEXT, scenario, ::assertKotlinShape)
    }
  }

  /** AI-generated test */
  @Test
  fun `lightweight commit replaces a complex when method body`() = runScenario(replaceBodyScenario)

  /** AI-generated test */
  @Test
  fun `lightweight commit inserts a documented generic member`() = runScenario(insertMemberScenario)

  /** AI-generated test */
  @Test
  fun `lightweight commit removes an enum entry and a nested declaration`() = runScenario(removeDeclarationsScenario)

  /** AI-generated test */
  @Test
  fun `lightweight commit applies replacements insertions and removals at once`() = runScenario(wideUpdateScenario)

  /**
   * AI-generated test
   */
  @Test
  fun `lightweight commit can be repeated on the same document`() {
    runVersionedTest(project) {
      repeat(4) {
        assertLightweightCommitScenario(project, editor.document, BASE_TEXT, replaceBodyScenario, ::assertKotlinShape)
      }
    }
  }
}
