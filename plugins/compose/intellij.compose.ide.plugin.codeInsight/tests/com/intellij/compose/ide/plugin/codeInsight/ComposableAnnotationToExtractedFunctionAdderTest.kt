/*
 * Copyright (C) 2020 The Android Open Source Project
 * Modified 2025 by JetBrains s.r.o.
 * Copyright (C) 2025 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.codeInsight

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.util.application
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractFunctionDescriptorModifier
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptorWithConflicts
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionGeneratorConfiguration
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractionResult
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.KotlinFirExtractFunctionHandler
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.ExtractionEngineHelper
import org.jetbrains.kotlin.idea.k2.refactoring.introduceConstant.INTRODUCE_CONSTANT
import org.jetbrains.kotlin.idea.k2.refactoring.introduceConstant.KotlinIntroduceConstantHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.EXTRACT_FUNCTION
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.ExtractionGeneratorOptions
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.name.FqName

class ComposableAnnotationToExtractedFunctionAdderTest : KotlinLightCodeInsightFixtureTestCase() {
  override fun runInDispatchThread(): Boolean = false
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
  }

  override fun setUp() {
    super.setUp()
    ExtractFunctionDescriptorModifier.EP_NAME.point.registerExtension(
      ComposableAnnotationToExtractedFunctionAdder(),
      testRootDisposable,
    )
  }

  /** Copied from AOSP. Regression test for https://issuetracker.google.com/issues/301481575 */
  fun testConstantInComposableFunction() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.runtime.Composable
      
      @Composable
      fun sourceFunction() {
          print(<selection>"foo"</selection>)
      }
    """.trimIndent())
      .invokingExtractConstant()
      .resultsIn("""
        import androidx.compose.runtime.Composable
        
        private const val string = "foo"
        
        @Composable
        fun sourceFunction() {
            print(string)
        }
      """.trimIndent())
  }

  fun `test inside non-Composable function`() {
    assertThatGivenComposeProjectWithState("""
      fun MyComposable() {
          <selection>println("a")</selection>
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        fun MyComposable() {
            newFunction()
        }
        
        private fun newFunction() {
            println("a")
        }
      """.trimIndent())
  }

  fun `test inside Composable function`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.foundation.layout.Column
      import androidx.compose.material.Text
      import androidx.compose.runtime.Composable
      
      @Composable
      fun MyComposable() {
          <selection>Column {
             Text("Hello")
             Text("World")
          }</selection>
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.foundation.layout.Column
        import androidx.compose.material.Text
        import androidx.compose.runtime.Composable

        @Composable
        fun MyComposable() {
            newFunction()
        }

        @Composable
        private fun newFunction() {
            Column {
                Text("Hello")
                Text("World")
            }
        }
      """.trimIndent())
  }

  fun `test inside non-Composable property getter`() {
    assertThatGivenComposeProjectWithState("""
      val myProp: Int
          get() {
              <selection>println("a")</selection>
              return 0
          }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        val myProp: Int
            get() {
                newFunction()
                return 0
            }

        private fun newFunction() {
            println("a")
        }
      """.trimIndent())
  }

  fun `test inside Composable property getter`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.foundation.layout.Column
      import androidx.compose.material.Text
      import androidx.compose.runtime.Composable
      
      val myProp: Int
          @Composable
          get() {
              <selection>Column {
                 Text("Hello")
                 Text("World")
              }</selection>
              return 0
          }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.foundation.layout.Column
        import androidx.compose.material.Text
        import androidx.compose.runtime.Composable
        
        val myProp: Int
            @Composable
            get() {
                newFunction()
                return 0
            }
        
        @Composable
        private fun newFunction() {
            Column {
                Text("Hello")
                Text("World")
            }
        }
      """.trimIndent())
  }

  fun `test inside deeply nested Composable property getter`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.foundation.layout.Column
      import androidx.compose.material.Text
      import androidx.compose.runtime.Composable
      
      fun f(@Composable block: () -> Unit)
      
      val myProp: Int
          @Composable
          get() {
              Column {
                Row {
                  LaunchedEffect(Unit) {
                    f {
                      <selection>Text("extract from here")</selection>
                    }
                  }
                }
              }
              return 0
          }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.foundation.layout.Column
        import androidx.compose.material.Text
        import androidx.compose.runtime.Composable
        
        fun f(@Composable block: () -> Unit)

        val myProp: Int
            @Composable
            get() {
                Column {
                  Row {
                    LaunchedEffect(Unit) {
                      f {
                          newFunction()
                      }
                    }
                  }
                }
                return 0
            }
        
        private fun newFunction() {
            Text("extract from here")
        }
      """.trimIndent())
  }

  fun `test inside property with explicit Composable type`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.material.Text
      import androidx.compose.runtime.Composable
  
      val myComposable: @Composable () -> Unit = {
          <selection>Text("Hello")</selection>
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.material.Text
        import androidx.compose.runtime.Composable
  
        val myComposable: @Composable () -> Unit = {
            newFunction()
        }
  
        @Composable
        private fun newFunction() {
            Text("Hello")
        }
      """.trimIndent())
  }

  fun `test inside non-inline Composable call with Composable lambda`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.foundation.layout.Column
      import androidx.compose.material.Text
      import androidx.compose.runtime.Composable
      
      @Composable
      fun MyComposable() {
          Column {
             <selection>Text("Hello")
             Text("World")</selection>
          }
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.foundation.layout.Column
        import androidx.compose.material.Text
        import androidx.compose.runtime.Composable
        
        @Composable
        fun MyComposable() {
            Column {
                newFunction()
            }
        }
        
        @Composable
        private fun newFunction() {
            Text("Hello")
            Text("World")
        }
      """.trimIndent())
  }

  fun `test inside inline Composable call with Composable lambda`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.foundation.layout.Column
      import androidx.compose.material.Text
      import androidx.compose.runtime.Composable
      
      @Composable
      inline fun View(block: @Composable () -> Unit) = Unit
      
      @Composable
      fun MyComposable() {
          View {
              <selection>Text("Hello")</selection>
          }
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.foundation.layout.Column
        import androidx.compose.material.Text
        import androidx.compose.runtime.Composable
        
        @Composable
        inline fun View(block: @Composable () -> Unit) = Unit
        
        @Composable
        fun MyComposable() {
            View {
                newFunction()
            }
        }
        
        @Composable
        private fun newFunction() {
            Text("Hello")
        }
      """.trimIndent())
  }

  fun `test inside non-inline Composable call with non-Composable lambda`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.foundation.layout.Column
      import androidx.compose.material.Text
      import androidx.compose.runtime.Composable

      @Composable
      fun View(block: () -> Unit) = Unit

      @Composable
      fun MyComposable() {
          View {
             <selection>println("Hello")</selection>
          }
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.foundation.layout.Column
        import androidx.compose.material.Text
        import androidx.compose.runtime.Composable

        @Composable
        fun View(block: () -> Unit) = Unit

        @Composable
        fun MyComposable() {
            View {
                newFunction()
            }
        }

        private fun newFunction() {
            println("Hello")
        }
      """.trimIndent())
  }

  fun `test inside inline Composable call with non-Composable lambda`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.foundation.layout.Column
      import androidx.compose.material.Text
      import androidx.compose.runtime.Composable
      
      @Composable
      inline fun View(block: () -> Unit) = Unit
      
      @Composable
      fun MyComposable() {
          View {
              <selection>println("Hello")</selection>
          }
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.foundation.layout.Column
        import androidx.compose.material.Text
        import androidx.compose.runtime.Composable
        
        @Composable
        inline fun View(block: () -> Unit) = Unit
        
        @Composable
        fun MyComposable() {
            View {
                newFunction()
            }
        }
        
        @Composable
        private fun newFunction() {
            println("Hello")
        }
      """.trimIndent())
  }

  fun `test inside inline non-Composable lambda - inside Composable lambda`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.foundation.layout.Column
      import androidx.compose.material.Text
      import androidx.compose.runtime.Composable
      
      @Composable
      fun MyComposable(a: Int?) {
          Column {
              a?.let {
                  <selection>Text("Hello")</selection>
              }
          }
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.foundation.layout.Column
        import androidx.compose.material.Text
        import androidx.compose.runtime.Composable
        
        @Composable
        fun MyComposable(a: Int?) {
            Column {
                a?.let {
                    newFunction()
                }
            }
        }
        
        @Composable
        private fun newFunction() {
            Text("Hello")
        }
      """.trimIndent())
  }

  fun `test inside inline non-Composable lambda - inside Composable lambda - disallow composable calls`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.foundation.layout.Column
      import androidx.compose.material.Text
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.DisallowComposableCalls

      inline fun f(block: @DisallowComposableCalls () -> Unit) = Unit

      @Composable
      fun MyComposable(a: Int?) {
          Column {
              f {
                  <selection>println("Hello")</selection>
              }
          }
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.foundation.layout.Column
        import androidx.compose.material.Text
        import androidx.compose.runtime.Composable
        import androidx.compose.runtime.DisallowComposableCalls

        inline fun f(block: @DisallowComposableCalls () -> Unit) = Unit

        @Composable
        fun MyComposable(a: Int?) {
            Column {
                f {
                    newFunction()
                }
            }
        }
        
        private fun newFunction() {
            println("Hello")
        }
      """.trimIndent())
  }

  fun `test inside 2 inline non-Composable lambdas - inside Composable lambda`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.foundation.layout.Column
      import androidx.compose.material.Text
      import androidx.compose.runtime.Composable
      
      @Composable
      fun MyComposable(a: Int?, b: Int) {
          Column {
              a?.let {
                  b.let {
                      <selection>Text("Hello")</selection>
                  }
              }
          }
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.foundation.layout.Column
        import androidx.compose.material.Text
        import androidx.compose.runtime.Composable
        
        @Composable
        fun MyComposable(a: Int?, b: Int) {
            Column {
                a?.let {
                    b.let {
                        newFunction()
                    }
                }
            }
        }
        
        @Composable
        private fun newFunction() {
            Text("Hello")
        }
      """.trimIndent())
  }

  fun `test inside inline non-Composable call with Composable lambda`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.foundation.layout.Column
      import androidx.compose.material.Text
      import androidx.compose.runtime.Composable

      inline fun view(block: @Composable () -> Unit) = Unit

      fun f() {
          view {
              <selection>Text("Hello")</selection>
          }
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.foundation.layout.Column
        import androidx.compose.material.Text
        import androidx.compose.runtime.Composable

        inline fun view(block: @Composable () -> Unit) = Unit

        fun f() {
            view {
                newFunction()
            }
        }

        @Composable
        private fun newFunction() {
            Text("Hello")
        }
      """.trimIndent())
  }

  fun `test inside non-inline non-Composable lambda`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.LaunchedEffect

      @Composable
      fun MyComposable() {
          LaunchedEffect(Unit) {
             <selection>println("Hello world")</selection>
          }
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.runtime.Composable
        import androidx.compose.runtime.LaunchedEffect

        @Composable
        fun MyComposable() {
            LaunchedEffect(Unit) {
                newFunction()
            }
        }

        private fun newFunction() {
            println("Hello world")
        }
      """.trimIndent())
  }

  fun `test inside inline non-Composable lambda - inside non-inline non-Composable lambda`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.LaunchedEffect
      import androidx.compose.material.Text
      
      @Composable
      fun MyComposable(a: Int?) {
          LaunchedEffect(Unit) {
             a?.let {
                 <selection>Text("Hello")</selection>
             }
          }
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.runtime.Composable
        import androidx.compose.runtime.LaunchedEffect
        import androidx.compose.material.Text
        
        @Composable
        fun MyComposable(a: Int?) {
            LaunchedEffect(Unit) {
               a?.let {
                   newFunction()
               }
            }
        }
        
        private fun newFunction() {
            Text("Hello")
        }
      """.trimIndent())
  }

  fun `test preserve existing annotations inside function`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.foundation.layout.Column
      import androidx.compose.material.Text
      import androidx.compose.runtime.Composable
      
      @Composable
      fun MyComposable() {
          <selection>Column {
              Text("Hello")
              Text("World")
          }</selection>
      }
    """.trimIndent())
      .invokingExtractFunctionWithExistingAnnotations(COMPOSE_PREVIEW_ANNOTATION_FQN)
      .resultsIn("""
        import androidx.compose.foundation.layout.Column
        import androidx.compose.material.Text
        import androidx.compose.runtime.Composable
        
        @Composable
        fun MyComposable() {
            newFunction()
        }
        
        @androidx.compose.ui.tooling.preview.Preview
        @Composable
        private fun newFunction() {
            Column {
                Text("Hello")
                Text("World")
            }
        }
      """.trimIndent())
  }

  fun `test preserve annotations from other analyzers inside lambda`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.foundation.layout.Column
      import androidx.compose.material.Text
      import androidx.compose.runtime.Composable
      
      @Composable
      fun MyComposable() {
          Column {
              <selection>Text("Hello")
              Text("World")</selection>
          }
      }
    """.trimIndent())
      .invokingExtractFunctionWithExistingAnnotations(COMPOSE_PREVIEW_ANNOTATION_FQN)
      .resultsIn("""
        import androidx.compose.foundation.layout.Column
        import androidx.compose.material.Text
        import androidx.compose.runtime.Composable

        @Composable
        fun MyComposable() {
            Column {
                newFunction()
            }
        }

        @androidx.compose.ui.tooling.preview.Preview
        @Composable
        private fun newFunction() {
            Text("Hello")
            Text("World")
        }
      """.trimIndent())
  }

  fun `test inside unresolved call lambda - inside Composable function`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.material.Text
      import androidx.compose.runtime.Composable
      
      @Composable
      fun MyComposable(a: Int?) {
          Column {
              <selection>Text("Hello")</selection>
          }
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.material.Text
        import androidx.compose.runtime.Composable
        
        @Composable
        fun MyComposable(a: Int?) {
            Column {
                newFunction()
            }
        }
        
        private fun newFunction() {
            Text("Hello")
        }
      """.trimIndent())
  }

  fun `test inside unresolved call lambda - inside non-Composable function`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.material.Text
      
      fun f() {
          Column {
              <selection>Text("Hello")</selection>
          }
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.material.Text
        
        fun f() {
            Column {
                newFunction()
            }
        }
        
        private fun newFunction() {
            Text("Hello")
        }
      """.trimIndent())
  }

  fun `test noinline lambda`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.runtime.Composable
      
      inline fun foo(noinline a: () -> Unit) {}
      
      @Composable
      fun f() {
          foo {
              <selection>println("Hello, World!")</selection>
          }
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.runtime.Composable
        
        inline fun foo(noinline a: () -> Unit) {}
        
        @Composable
        fun f() {
            foo {
                newFunction()
            }
        }
        
        private fun newFunction() {
            println("Hello, World!")
        }
      """.trimIndent())
  }

  fun `test crossinline lambda`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.runtime.Composable
      
      inline fun foo(crossinline a: () -> Unit) {}
      
      @Composable
      fun f() {
          foo {
              <selection>println("Hello, World!")</selection>
          }
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.runtime.Composable
        
        inline fun foo(crossinline a: () -> Unit) {}
        
        @Composable
        fun f() {
            foo {
                newFunction()
            }
        }
        
        private fun newFunction() {
            println("Hello, World!")
        }
      """.trimIndent())
  }

  fun `test noinline lambda explicitly Composable`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.runtime.Composable
      
      inline fun foo(noinline a: @Composable () -> Unit) {}
      
      @Composable
      fun f() {
          foo {
              <selection>println("Hello, World!")</selection>
          }
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.runtime.Composable
        
        inline fun foo(noinline a: @Composable () -> Unit) {}
        
        @Composable
        fun f() {
            foo {
                newFunction()
            }
        }
        
        @Composable
        private fun newFunction() {
            println("Hello, World!")
        }
      """.trimIndent())
  }

  fun `test crossinline lambda explicitly Composable`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.runtime.Composable
      
      inline fun foo(crossinline a: @Composable () -> Unit) {}
      
      @Composable
      fun f() {
          foo {
              <selection>println("Hello, World!")</selection>
          }
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.runtime.Composable
        
        inline fun foo(crossinline a: @Composable () -> Unit) {}
        
        @Composable
        fun f() {
            foo {
                newFunction()
            }
        }
        
        @Composable
        private fun newFunction() {
            println("Hello, World!")
        }
      """.trimIndent())
  }

  fun `test lambda that is not argument`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.runtime.Composable
      
      @Composable
      fun f() {
          val a = {
              <selection>println("Hello, World!")</selection>
          }
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.runtime.Composable
        
        @Composable
        fun f() {
            val a = {
                newFunction()
            }
        }
        
        private fun newFunction() {
            println("Hello, World!")
        }
      """.trimIndent())
  }

  fun `test lambda that is not argument with explicit composability`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.runtime.Composable
      
      @Composable
      fun f() {
          val a = @Composable {
              <selection>println("Hello, World!")</selection>
          }
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.runtime.Composable
        
        @Composable
        fun f() {
            val a = @Composable {
                newFunction()
            }
        }
        
        @Composable
        private fun newFunction() {
            println("Hello, World!")
        }
      """.trimIndent())
  }

  fun `test inside returned lambda from function with Composable return type`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.material.Text
      import androidx.compose.runtime.Composable
  
      fun returnsComposable(): @Composable () -> Unit {
          return {
              <selection>Text("Hello")</selection>
          }
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.material.Text
        import androidx.compose.runtime.Composable
  
        fun returnsComposable(): @Composable () -> Unit {
            return {
                newFunction()
            }
        }
  
        @Composable
        private fun newFunction() {
            Text("Hello")
        }
      """.trimIndent())
  }

  fun `test inside returned lambda from function with non-Composable return type`() {
    assertThatGivenComposeProjectWithState("""
        import androidx.compose.runtime.Composable
    
        fun returnsLambda(): () -> Unit {
            return {
                <selection>println("Hello")</selection>
            }
        }
      """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.runtime.Composable
  
        fun returnsLambda(): () -> Unit {
            return {
                newFunction()
            }
        }
  
        private fun newFunction() {
            println("Hello")
        }
      """.trimIndent())
  }

  fun `test Composable is not imported`() {
    assertThatGivenComposeProjectWithState("""
      @Composable
      fun MyComposable() {
          <selection>Text("World")</selection>
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        @Composable
        fun MyComposable() {
            newFunction()
        }
        
        private fun newFunction() {
            Text("World")
        }
      """.trimIndent())
  }

  fun `test inside named composable argument`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.material.Text
      import androidx.compose.runtime.Composable
  
      fun withComposableParam(content: @Composable () -> Unit) {}
  
      fun f() {
          withComposableParam(content = {
              <selection>Text("Hello")</selection>
          })
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.material.Text
        import androidx.compose.runtime.Composable
  
        fun withComposableParam(content: @Composable () -> Unit) {}
  
        fun f() {
            withComposableParam(content = {
                newFunction()
            })
        }
  
        @Composable
        private fun newFunction() {
            Text("Hello")
        }
      """.trimIndent())
  }

  fun `test inside mixed named composable argument`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.runtime.Composable
  
      suspend fun mixedNamed(
          composable: @Composable () -> Unit,
          suspendBlock: suspend () -> Unit
      ) {}
  
      fun f() {
          mixedNamed(
              composable = {
                  <selection>Text("Hello")</selection>
              },
              suspendBlock = {
                  println("World")
              }
          )
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.runtime.Composable
  
        suspend fun mixedNamed(
            composable: @Composable () -> Unit,
            suspendBlock: suspend () -> Unit
        ) {}
    
        fun f() {
            mixedNamed(
                composable = {
                    newFunction()
                },
                suspendBlock = {
                    println("World")
                }
            )
        }
        
        @Composable
        private fun newFunction() {
            Text("Hello")
        }
      """.trimIndent())
  }

  fun `test inside mixed named non-composable argument`() {
    assertThatGivenComposeProjectWithState("""
      import androidx.compose.runtime.Composable
  
      suspend fun mixedNamed(
          composable: @Composable () -> Unit,
          suspendBlock: suspend () -> Unit
      ) {}
  
      fun f() {
          mixedNamed(
              composable = {
                  Text("Hello")
              },
              suspendBlock = {
                  <selection>println("World")</selection>
              }
          )
      }
    """.trimIndent())
      .invokingExtractFunction()
      .resultsIn("""
        import androidx.compose.runtime.Composable
  
        suspend fun mixedNamed(
            composable: @Composable () -> Unit,
            suspendBlock: suspend () -> Unit
        ) {}
    
        fun f() {
            mixedNamed(
                composable = {
                    Text("Hello")
                },
                suspendBlock = {
                    newFunction()
                }
            )
        }
  
        private fun newFunction() {
            println("World")
        }
      """.trimIndent())
  }

  fun `test Composable is not on classpath`() {
    myFixture.configureByText(
      "test.kt",
      """
        import androidx.compose.material.Text

        @Composable
        fun MyComposable() {
            <selection>Text("World")</selection>
        }
      """.trimIndent()
    )

    myFixture.invokeExtractFunctionIn(application, existingAnnotationFqNames = emptyList())

    myFixture.checkResult("""
      import androidx.compose.material.Text

      @Composable
      fun MyComposable() {
          newFunction()
      }

      private fun newFunction() {
          Text("World")
      }
    """.trimIndent())
  }

  private fun ComposeProjectStateWithRefactoring.resultsIn(@Language("kt") expectedResult: String) {
    myFixture.configureStubbedComposeRuntime()
    myFixture.configureStubbedComposeFoundation()
    myFixture.configureStubbedComposeMaterial()
    myFixture.configureStubbedStandardLibraryLet()
    myFixture.configureByText("test.kt", initialState.text)

    if (isConstantExtractionInsteadOfFunction) {
      myFixture.invokeExtractConstantIn(application)
    }
    else {
      myFixture.invokeExtractFunctionIn(application, existingAnnotationFqNames)
    }

    myFixture.checkResult(expectedResult)
  }

  fun JavaCodeInsightTestFixture.invokeExtractFunctionIn(application: Application, existingAnnotationFqNames: List<FqName>) {
    val helper = ExtractionHelper(existingAnnotationFqNames)
    application.invokeAndWait {
      KotlinFirExtractFunctionHandler(helper = helper)
        .invoke(this.project, this.editor, this.file!!, null)
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    }
  }

  fun JavaCodeInsightTestFixture.invokeExtractConstantIn(application: Application) {
    val helper = InteractiveExtractionHelper()
    application.invokeAndWait {
      KotlinIntroduceConstantHandler(helper = helper)
        .invoke(this.project, this.editor, this.file!!, null)
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    }
  }

}

// Following 2 are heavily inspired/copied from AOSP
private class ExtractionHelper(private val existingAnnotationFqNames: List<FqName>) : ExtractionEngineHelper(EXTRACT_FUNCTION) {
  @OptIn(KaAllowAnalysisOnEdt::class)
  override fun configureAndRun(
    project: Project,
    editor: Editor,
    descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
    onFinish: (ExtractionResult) -> Unit,
  ) {
    // `descriptorWithConflicts.descriptor.copy(..)` runs the constructor of
    // ExtractableCodeDescriptor
    // that calls AA for return type check. Since the copied one has the exactly same type as the
    // initial descriptor, if we only copy the boolean value, we do not need `allowAnalysisOnEdt`.
    // To do so, we have to update `ExtractableCodeDescriptor`.
    allowAnalysisOnEdt {
      val newDescriptor =
        descriptorWithConflicts.descriptor.copy(
          suggestedNames = listOf("newFunction"),
          renderedAnnotations = existingAnnotationFqNames.map { "@${it.asString()}" } +
                                descriptorWithConflicts.descriptor.renderedAnnotations
        )
      doRefactor(
        ExtractionGeneratorConfiguration(newDescriptor, ExtractionGeneratorOptions.DEFAULT)
      ) { er: ExtractionResult ->
        onFinish(er)
      }
    }
  }
}

private class InteractiveExtractionHelper : ExtractionEngineHelper(INTRODUCE_CONSTANT) {
  override fun validate(
    descriptor: ExtractableCodeDescriptor
  ): ExtractableCodeDescriptorWithConflicts =
    KotlinIntroduceConstantHandler.InteractiveExtractionHelper.validate(descriptor)

  override fun configureAndRun(
    project: Project,
    editor: Editor,
    descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
    onFinish: (ExtractionResult) -> Unit,
  ) {
    KotlinIntroduceConstantHandler.InteractiveExtractionHelper.configureAndRun(
      project,
      editor,
      descriptorWithConflicts,
    ) { er: ExtractionResult ->
      onFinish(er)
    }
  }
}


data class ComposeProjectState(val text: String)

data class ComposeProjectStateWithRefactoring(
  val initialState: ComposeProjectState,
  val isConstantExtractionInsteadOfFunction: Boolean,
  val existingAnnotationFqNames: List<FqName>,
)

fun assertThatGivenComposeProjectWithState(@Language("kt") editorStateText: String): ComposeProjectState =
  ComposeProjectState(editorStateText)

fun ComposeProjectState.invokingExtractFunction(): ComposeProjectStateWithRefactoring =
  invokingExtractFunctionWithExistingAnnotations()

fun ComposeProjectState.invokingExtractFunctionWithExistingAnnotations(vararg names: FqName): ComposeProjectStateWithRefactoring =
  ComposeProjectStateWithRefactoring(this, isConstantExtractionInsteadOfFunction = false, names.toList())

fun ComposeProjectState.invokingExtractConstant(): ComposeProjectStateWithRefactoring =
  ComposeProjectStateWithRefactoring(this, isConstantExtractionInsteadOfFunction = true, emptyList())

val COMPOSE_PREVIEW_ANNOTATION_FQN = FqName("androidx.compose.ui.tooling.preview.Preview")

fun JavaCodeInsightTestFixture.configureStubbedStandardLibraryLet() {
  addFileToProject(
    "Let.kt",
    """
      // Should be `package kotlin`, but then it doesn't work without import. So I just put it in root package as a hack.

      inline fun <T, R> T.let(block: (T) -> R): R = block(this)
    """.trimIndent()
  )
}

fun JavaCodeInsightTestFixture.configureStubbedComposeMaterial() {
  addFileToProject(
    "Material.kt",
    """
      package androidx.compose.material

      import androidx.compose.runtime.Composable

      @Composable
      fun Text(text: String) = Unit
    """.trimIndent()
  )
}

fun JavaCodeInsightTestFixture.configureStubbedComposeFoundation() {
  addFileToProject(
    "Foundation.kt",
    """
      package androidx.compose.foundation.layout

      import androidx.compose.runtime.Composable

      @Composable
      fun Column(block: @Composable () -> Unit) = Unit
    """.trimIndent()
  )
}

fun JavaCodeInsightTestFixture.configureStubbedComposeRuntime() {
  addFileToProject(
    "Runtime.kt",
    """       
      package androidx.compose.runtime

      annotation class Composable
      annotation class DisallowComposableCalls

      @Composable
      fun LaunchedEffect(key: Unit, block: suspend () -> Unit) = Unit
    """.trimIndent()
  )
}