// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestIndexingModeSupporter
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.*
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.indexing.FileBasedIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.kotlin.inspections.KotlinCheckedExceptionInspection
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.junit.jupiter.api.*
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.readText

@UseK2PluginMode
@TestApplication
class KotlinCheckedExceptionsInspectionTest {
  private lateinit var myFixture: JavaCodeInsightTestFixture

  companion object {
    @BeforeAll
    @AfterAll
    @JvmStatic
    fun clearStubs() {
      // For some reason, other tests may fail after this test if there's no index clearing.
      FileBasedIndex.getInstance().requestRebuild(StubUpdatingIndex.INDEX_ID)
    }
  }

  @BeforeEach
  fun initInspection() {
    val fixture = IdeaTestFixtureFactory
      .getFixtureFactory()
      .createLightFixtureBuilder(getProjectDescriptor(), KotlinCheckedExceptionsInspectionTest::class.simpleName!!)
      .getFixture()
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, LightTempDirTestFixtureImpl(true))
    myFixture = JavaIndexingModeCodeInsightTestFixture.wrapFixture(myFixture, TestIndexingModeSupporter.IndexingMode.SMART)

    myFixture.setTestDataPath(getTestDataPath())
    myFixture.setUp()

    myFixture.enableInspections(KotlinCheckedExceptionInspection::class.java as Class<LocalInspectionTool>)
    myFixture.allowTreeAccessForAllFiles()

    val annotation =
      Path(PathManager.getCommunityHomePath())
        .resolve("platform/eel/src/com/intellij/platform/eel/ThrowsChecked.kt")
        .readText()
    myFixture.configureByText("Annotation.kt", annotation)
  }

  @AfterEach
  fun tearDownFixture() {
    if (::myFixture.isInitialized) {
      myFixture.tearDown()
    }
  }

  private fun getTestDataPath(): String {
    val communityPath = PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/')
    val path = communityPath
    return if (File(path).exists()) path else "$communityPath/../"
  }

  fun getProjectDescriptor(): LightProjectDescriptor = object : DefaultLightProjectDescriptor() {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      val kotlinStdlibName = "kotlin-stdlib"
      val kotlinStdlibPaths = IntelliJProjectConfiguration.getProjectLibrary(kotlinStdlibName)
      val kotlinStdlib = model.moduleLibraryTable.createLibrary(kotlinStdlibName)

      kotlinStdlib.modifiableModel.apply {
        for (rootUrl in kotlinStdlibPaths.classesUrls) {
          addRoot(rootUrl, OrderRootType.CLASSES)
        }
        for (rootUrl in kotlinStdlibPaths.sourcesUrls) {
          addRoot(rootUrl, OrderRootType.SOURCES)
        }
        commit()
      }
    }
  }

  @Test
  fun `positive report`() {
    @Language("kotlin")
    val lib = """
      import com.intellij.platform.eel.ThrowsChecked

      class MyException : Exception()

      @ThrowsChecked(MyException::class)
      fun someAction() {
        error()
      }

      fun placeholder() {}
    """.trimIndent()

    @Language("kotlin")
    val source = """
      fun someActionUsage() {
        placeholder()
        <warning descr="Unchecked exceptions: MyException">someAction()</warning>
        placeholder()
      }
    """.trimIndent()

    myFixture.configureByText("Lib.kt", lib)
    myFixture.configureByText("Source.kt", source)

    myFixture.testHighlighting("Source.kt")
  }

  @Test
  fun `multiple throws`() {
    @Language("kotlin")
    val lib = """
      import com.intellij.platform.eel.ThrowsChecked

      class MyException1 : Exception()
      class MyException2 : Exception()
      class MyException3 : Exception()

      @ThrowsChecked(MyException1::class)
      @ThrowsChecked(MyException2::class, MyException3::class)
      fun someAction() {
        error()
      }

      fun placeholder() {}
    """.trimIndent()

    @Language("kotlin")
    val source = """
      fun someActionUsage() {
        placeholder()
        <warning descr="Unchecked exceptions: MyException1, MyException2, MyException3">someAction()</warning>
        placeholder()
      }
    """.trimIndent()

    myFixture.configureByText("Lib.kt", lib)
    myFixture.configureByText("Source.kt", source)

    myFixture.testHighlighting("Source.kt")
  }

  @Test
  fun rethrows() {
    @Language("kotlin")
    val lib = """
      import com.intellij.platform.eel.ThrowsChecked

      class MyException : Exception()

      @ThrowsChecked(MyException::class)
      fun someAction() {
        error()
      }

      fun placeholder() {}
    """.trimIndent()

    @Language("kotlin")
    val source = """
      import com.intellij.platform.eel.ThrowsChecked

      @ThrowsChecked(MyException::class)
      fun someActionUsage() {
        placeholder()
        someAction()
        placeholder()
      }
    """.trimIndent()

    myFixture.configureByText("Lib.kt", lib)
    myFixture.configureByText("Source.kt", source)

    myFixture.testHighlighting("Source.kt")
  }

  @Test
  fun `no throws annotation to rethrow`() {
    @Language("kotlin")
    val lib = """
      import com.intellij.platform.eel.ThrowsChecked

      class MyException1 : Exception()
      class MyException2 : Exception()

      @ThrowsChecked(MyException1::class)
      @ThrowsChecked(MyException2::class)
      fun someAction() {
        error()
      }

      fun placeholder() {}
    """.trimIndent()

    @Language("kotlin")
    val source = """
      import com.intellij.platform.eel.ThrowsChecked

      @ThrowsChecked(MyException1::class)
      fun someActionUsage() {
        placeholder()
        <warning descr="Unchecked exceptions: MyException2">someAction()</warning>
        placeholder()
      }
    """.trimIndent()

    myFixture.configureByText("Lib.kt", lib)
    myFixture.configureByText("Source.kt", source)

    myFixture.testHighlighting("Source.kt")
  }

  @Test
  fun `full try-catch`() {
    @Language("kotlin")
    val lib = """
      import com.intellij.platform.eel.ThrowsChecked

      class MyException : Exception()

      @ThrowsChecked(MyException::class)
      fun someAction() {
        error()
      }

      fun placeholder() {}
    """.trimIndent()

    @Language("kotlin")
    val source = """
      fun someActionUsage() {
        placeholder()
        try {
          someAction()
        }
        catch (_: MyException) {
          // Nothing.
        }
        placeholder()
      }
    """.trimIndent()

    myFixture.configureByText("Lib.kt", lib)
    myFixture.configureByText("Source.kt", source)

    myFixture.testHighlighting("Source.kt")
  }

  @Test
  fun `partial try-catch`() {
    @Language("kotlin")
    val lib = """
      import com.intellij.platform.eel.ThrowsChecked

      class MyException1 : Exception()
      class MyException2 : Exception()

      @ThrowsChecked(MyException1::class, MyException2::class)
      fun someAction() {
        error()
      }

      fun placeholder() {}
    """.trimIndent()

    @Language("kotlin")
    val source = """
      fun someActionUsage() {
        placeholder()
        try {
          <warning descr="Unchecked exceptions: MyException2">someAction()</warning>
        }
        catch (_: MyException1) {
          // Nothing.
        }
        placeholder()
      }
    """.trimIndent()

    myFixture.configureByText("Lib.kt", lib)
    myFixture.configureByText("Source.kt", source)

    myFixture.testHighlighting("Source.kt")
  }

  @Test
  fun `try-catch and rethrow`() {
    @Language("kotlin")
    val lib = """
      import com.intellij.platform.eel.ThrowsChecked

      class MyException1 : Exception()
      class MyException2 : Exception()
      class MyException3 : Exception()

      @ThrowsChecked(MyException1::class, MyException2::class, MyException3::class)
      fun someAction() {
        error()
      }

      fun placeholder() {}
    """.trimIndent()

    @Language("kotlin")
    val source = """
      import com.intellij.platform.eel.ThrowsChecked

      @ThrowsChecked(MyException2::class)
      fun someActionUsage() {
        placeholder()
        try {
          <warning descr="Unchecked exceptions: MyException3">someAction()</warning>
        }
        catch (_: MyException1) {
          // Nothing.
        }
        placeholder()
      }
    """.trimIndent()

    myFixture.configureByText("Lib.kt", lib)
    myFixture.configureByText("Source.kt", source)

    myFixture.testHighlighting("Source.kt")
  }

  @Test
  fun `full try-catch through a base class`() {
    @Language("kotlin")
    val lib = """
      import com.intellij.platform.eel.ThrowsChecked

      class MyExceptionBase : Exception()
      class MyException1 : MyExceptionBase()
      class MyException2 : MyExceptionBase()
      class MyDetachedException : Exception()

      @ThrowsChecked(MyException1::class, MyException2::class, MyDetachedException::class)
      fun someAction() {
        error()
      }

      fun placeholder() {}
    """.trimIndent()

    @Language("kotlin")
    val source = """
      fun someActionUsage() {
        placeholder()
        try {
          <warning descr="Unchecked exceptions: MyDetachedException">someAction()</warning>
        }
        catch (_: MyExceptionBase) {
          // Nothing.
        }
        try {
          someAction()
        }
        catch (_: Throwable) {
          // Nothing.
        }
        placeholder()
      }
    """.trimIndent()

    myFixture.configureByText("Lib.kt", lib)
    myFixture.configureByText("Source.kt", source)

    myFixture.testHighlighting("Source.kt")
  }

  @Test
  fun `add annotation quick-fix for named function`(): Unit = timeoutRunBlocking {
    @Language("kotlin")
    val lib = """
      import com.intellij.platform.eel.ThrowsChecked

      class MyException : Exception()

      object MyObject {
        @ThrowsChecked(MyException::class)
        fun someAction(): Int {
          error()
        }
      }

      fun placeholder() {}
    """.trimIndent()

    @Language("kotlin")
    val initialSource = """
      fun someActionUsage() {
        run {
          listOf(1, 2, 3).forEach { 
            MyObject.someA<caret>ction()
          }
        }

        val someFn: () -> Unit = {
          MyObject.someAction()
        }
      }
    """.trimIndent()

    myFixture.configureByText("Lib.kt", lib)
    val sourceFile = myFixture.configureByText("Source.kt", initialSource)

    withContext(Dispatchers.EDT) {
      myFixture.openFileInEditor(sourceFile.virtualFile)
    }

    val intention = myFixture.findSingleIntention("Add annotations for re-throwing checked exceptions")
    myFixture.launchAction(intention)

    @Language("kotlin")
    val expectedSource = """
      import com.intellij.platform.eel.ThrowsChecked

      @ThrowsChecked(MyException::class)
      fun someActionUsage() {
        run {
          listOf(1, 2, 3).forEach { 
            MyObject.someA<caret>ction()
          }
        }

        val someFn: () -> Unit = {
          MyObject.someAction()
        }
      }
    """.trimIndent()

    myFixture.checkResult(expectedSource)
  }

  @Test
  fun `add annotation quick-fix for lambda variable with explicit type`(): Unit = timeoutRunBlocking {
    @Language("kotlin")
    val lib = """
      import com.intellij.platform.eel.ThrowsChecked

      class MyException : Exception()

      object MyObject {
        @ThrowsChecked(MyException::class)
        fun someAction(): Int {
          error()
        }
      }

      fun placeholder() {}
    """.trimIndent()

    @Language("kotlin")
    val initialSource = """
      fun someActionUsage() {
        run {
          listOf(1, 2, 3).forEach { 
            MyObject.someAction()
          }
        }

        val someFn: () -> Unit = {
          MyObject.some<caret>Action()
        }
      }
    """.trimIndent()

    myFixture.configureByText("Lib.kt", lib)
    val sourceFile = myFixture.configureByText("Source.kt", initialSource)

    withContext(Dispatchers.EDT) {
      myFixture.openFileInEditor(sourceFile.virtualFile)
    }

    val intention = myFixture.findSingleIntention("Add annotations for re-throwing checked exceptions")
    myFixture.launchAction(intention)

    @Language("kotlin")
    val expectedSource = """
      import com.intellij.platform.eel.ThrowsChecked

      fun someActionUsage() {
        run {
          listOf(1, 2, 3).forEach { 
            MyObject.someAction()
          }
        }

        val someFn: @ThrowsChecked(MyException::class) () -> Unit = {
          MyObject.someAction()
        }
      }
    """.trimIndent()

    myFixture.checkResult(expectedSource)
  }

  @Test
  fun `add annotation quick-fix for lambda variable with implicit type`(): Unit = timeoutRunBlocking {
    @Language("kotlin")
    val lib = """
      import com.intellij.platform.eel.ThrowsChecked

      class MyException : Exception()

      object MyObject {
        @ThrowsChecked(MyException::class)
        fun someAction(): Int {
          error()
        }
      }

      fun placeholder() {}
    """.trimIndent()

    @Language("kotlin")
    val initialSource = """
      fun someActionUsage() {
        run {
          listOf(1, 2, 3).forEach { 
            MyObject.someAction()
          }
        }

        val someFn = {
          MyObject.some<caret>Action()
        }
      }
    """.trimIndent()

    myFixture.configureByText("Lib.kt", lib)
    val sourceFile = myFixture.configureByText("Source.kt", initialSource)

    withContext(Dispatchers.EDT) {
      myFixture.openFileInEditor(sourceFile.virtualFile)
    }

    val intention = myFixture.findSingleIntention("Add annotations for re-throwing checked exceptions")
    myFixture.launchAction(intention)

    @Language("kotlin")
    val expectedSource = """
      import com.intellij.platform.eel.ThrowsChecked

      fun someActionUsage() {
        run {
          listOf(1, 2, 3).forEach { 
            MyObject.someAction()
          }
        }

        val someFn: @ThrowsChecked(MyException::class) () -> Int = {
          MyObject.someAction()
        }
      }
    """.trimIndent()

    myFixture.checkResult(expectedSource)
  }

  @Test
  fun `surround with try-catch quick-fix`(): Unit = timeoutRunBlocking {
    @Language("kotlin")
    val lib = """
      import com.intellij.platform.eel.ThrowsChecked

      class MyException : Exception()

      object MyObject {
        @ThrowsChecked(MyException::class)
        fun someAction(): Int {
          error()
        }
      }

      fun placeholder() {}
    """.trimIndent()

    @Language("kotlin")
    val initialSource = """
      fun someActionUsage() {
          placeholder()
          println(123 + MyObject.someA<caret>ction() + 456)
          placeholder()
      }
    """.trimIndent()

    myFixture.configureByText("Lib.kt", lib)
    val sourceFile = myFixture.configureByText("Source.kt", initialSource)

    withContext(Dispatchers.EDT) {
      myFixture.openFileInEditor(sourceFile.virtualFile)
    }

    val intention = myFixture.findSingleIntention("Surround with try/catch")
    myFixture.launchAction(intention)

    @Language("kotlin")
    val expectedSource = """
      fun someActionUsage() {
          try <caret>{
              placeholder()
              println(123 + MyObject.someAction() + 456)
              placeholder()
          } catch (err: MyException) {
              TODO("Unhandled exception ${'$'}err")
          }
      }
    """.trimIndent()

    myFixture.checkResult(expectedSource)
  }


  @Test
  fun `aware of functions that executes lambdas once withAnnotation`() {
    doLambdaTest("""
      import com.intellij.platform.eel.ThrowsChecked

      @ThrowsChecked(MyException::class)
      fun withAnnotation() {
        executeOnce {
          someAction()
        }
        executeNoOneKnowsWhen {
          <warning descr="Unchecked exceptions: MyException">someAction()</warning>
        }
      }
    """.trimIndent())
  }

  @Test
  fun `aware of functions that executes lambdas once withAnnotationNestedCalls`() {
    doLambdaTest("""
      import com.intellij.platform.eel.ThrowsChecked

      @ThrowsChecked(MyException::class)
      fun withAnnotationNestedCalls() {
        executeOnce {
          executeOnce {
            executeOnce {
              someAction()
            }
          }
        }
        executeOnce {
          executeOnce {
            executeOnce {
              executeNoOneKnowsWhen {
                <warning descr="Unchecked exceptions: MyException">someAction()</warning>
              }
            }
          }
        }
      }
    """.trimIndent())
  }

  @Test
  fun `aware of functions that executes lambdas once withAnnotationAnotherSyntax`() {
    doLambdaTest("""
      import com.intellij.platform.eel.ThrowsChecked

      @ThrowsChecked(MyException::class)
      fun withAnnotationAnotherSyntax() {
        executeOnce(123, { someAction() })
        executeOnce(
          block = { someAction() },
          someConstant = 123,
        )
      }
    """.trimIndent())
  }

  @Test
  fun `aware of functions that executes lambdas once tryAround`() {
    doLambdaTest("""
      fun tryAround() {
        try {
          executeOnce {
            someAction()
          }
          executeNoOneKnowsWhen {
            <warning descr="Unchecked exceptions: MyException">someAction()</warning>
          }
        } catch (_: MyException) {}
      }
    """.trimIndent())
  }

  @Test
  fun `aware of functions that executes lambdas once tryAroundAndOtherKnownLambdasWithContracts`() {
    doLambdaTest("""
      fun tryAroundAndOtherKnownLambdasWithContracts() {
        try {
          run {
            someAction()
          }
        } catch (_: MyException) {}

        run {
          <warning descr="Unchecked exceptions: MyException">someAction()</warning>
        }
      }
    """.trimIndent())
  }

  @Test
  fun `aware of functions that executes lambdas once withAnnotationAndCollections`() {
    doLambdaTest("""
      fun withAnnotationAndCollections() {
        try {
          listOf(1, 2, 3).map {
            someAction()
            false
          }
        } catch (_: MyException) {}

        listOf(1, 2, 3).map {
          <warning descr="Unchecked exceptions: MyException">someAction()</warning>
          false
        }
      }
    """.trimIndent())
  }

  @Test
  fun `aware of functions that executes lambdas once tryInside`() {
    doLambdaTest("""
      fun tryInside() {
        executeOnce {
          try {
            someAction()
          } catch (_: MyException) {}
        }
        executeNoOneKnowsWhen {
          try {
            someAction()
          } catch (_: MyException) {}
        }
      }
    """.trimIndent())
  }

  @Test
  fun `aware of functions that executes lambdas once ignoreErrors`() {
    doLambdaTest("""
      fun ignoreErrors() {
        executeOnce {
          <warning descr="Unchecked exceptions: MyException">someAction()</warning>
        }
        executeNoOneKnowsWhen {
          <warning descr="Unchecked exceptions: MyException">someAction()</warning>
        }
      }
    """.trimIndent())
  }

  @Test
  fun `annotated function types`() {
    @Language("kotlin")
    val source = """
      import com.intellij.platform.eel.ThrowsChecked
      
      class MyException : Exception()

      @ThrowsChecked(MyException::class)
      fun someAction() {
        throw MyException()
      }

      fun specialHandler(body: @ThrowsChecked(MyException::class) () -> Unit) {
        try {
          body()
        } catch (_: MyException) {}

        <warning descr="Unchecked exceptions: MyException">body()</warning>
      }

      fun useOfSpecialHandler() {
        specialHandler {
          someAction()
        }
      }
    """.trimIndent()

    myFixture.configureByText("Source.kt", source)
    myFixture.testHighlighting("Source.kt")
  }

  private fun doLambdaTest(@Language("kotlin") source: String) {
    @Language("kotlin")
    val lib = """
      import kotlin.contracts.ExperimentalContracts
      import kotlin.contracts.contract

      import com.intellij.platform.eel.ThrowsChecked
      import java.io.Closeable

      class MyException : Exception()

      @ThrowsChecked(MyException::class)
      fun someAction() {
        error()
      }

      @OptIn(ExperimentalContracts::class)
      fun executeOnce(someConstant: Int = 123, block: () -> Unit) {
        contract {
          callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
        }
        println(someConstant)
        block()
      }

      fun executeNoOneKnowsWhen(block: () -> Unit) { }
    """.trimIndent()

    myFixture.configureByText("Lib.kt", lib)
    myFixture.configureByText("Source.kt", source)

    myFixture.testHighlighting("Source.kt")
  }
}