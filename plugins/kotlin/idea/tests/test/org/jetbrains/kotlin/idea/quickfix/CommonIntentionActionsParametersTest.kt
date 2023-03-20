// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.lang.jvm.actions.*
import com.intellij.lang.jvm.types.JvmType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.uast.UMethod
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class CommonIntentionActionsParametersTest : LightPlatformCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

    override fun setUp() {
        super.setUp()
        myFixture.configureByText("Anno.kt", "annotation class Anno(val i: Int)")
        myFixture.configureByText("ClassRef.kt", "annotation class ClassRef(val klass: KClass<*>)")
        myFixture.configureByText("Root.kt", "annotation class Root(val children: Array<Child>")
        myFixture.configureByText("Child.kt", "annotation class Child(val s: String)")
    }

    fun testSetParameters() {
        myFixture.configureByText(
            "foo.kt",
            """
            class Foo {
                fun ba<caret>r() {}
            }
            """.trimIndent()
        )

        myFixture.launchAction(
            createChangeParametersActions(
                myFixture.atCaret<UMethod>().javaPsi,
                setMethodParametersRequest(
                    linkedMapOf<String, JvmType>(
                      "i" to PsiTypes.intType(),
                      "file" to PsiType.getTypeByName("java.io.File", project, myFixture.file.resolveScope)
                    ).entries
                )
            ).findWithText("Change method parameters to '(i: Int, file: File)'")
        )
        myFixture.checkResult(
            """
            import java.io.File

            class Foo {
                fun bar(i: Int, file: File) {}
            }
            """.trimIndent(),
            true
        )
    }

  fun testSetConstructorParameters() {
      myFixture.configureByText(
          "foo.kt",
           """
           class Foo(<caret>) {
           }
           """.trimIndent()
      )

    val action = createChangeParametersActions(
        myFixture.atCaret<UMethod>().javaPsi,
        setMethodParametersRequest(
            linkedMapOf<String, JvmType>(
              "i" to PsiTypes.intType(),
              "file" to PsiType.getTypeByName("java.io.File", project, myFixture.file.resolveScope)
            ).entries
        )
    ).find { it.text.contains("Change signature of Foo") }
    if (action == null) kotlin.test.fail("Change signature intention not found")
    myFixture.launchAction(action)
    myFixture.checkResult(
        """
        import java.io.File

        class Foo(i: Int, file: File) {
        }
        """.trimIndent(),
        true
    )
  }

    fun testAddParameterToTheEnd() {
        myFixture.configureByText(
            "foo.kt",
            """
            class Foo {
                fun ba<caret>r(@Anno(3) a: Int) {}
            }
            """.trimIndent()
        )

        runParametersTransformation("Change method parameters to '(a: Int, file: File)'") { currentParameters ->
            currentParameters + expectedParameter(
                PsiType.getTypeByName("java.io.File", project, myFixture.file.resolveScope), "file",
                listOf(annotationRequest("Anno", intAttribute("i", 8)))
            )
        }

        myFixture.checkResult(
            """
            import java.io.File

            class Foo {
                fun bar(@Anno(3) a: Int, @Anno(i = 8) file: File) {}
            }
            """.trimIndent(), true
        )
    }


    fun testAddParameterToTheBeginning() {
        myFixture.configureByText(
            "foo.kt",
            """
            class Foo {
                fun ba<caret>r(@Anno(3) a: Int) {}
            }
            """.trimIndent()
        )

        runParametersTransformation("Change method parameters to '(file: File, a: Int)'") { currentParameters ->
            listOf(
                expectedParameter(
                    PsiType.getTypeByName("java.io.File", project, myFixture.file.resolveScope), "file",
                    listOf(annotationRequest("Anno", intAttribute("i", 8)))
                )
            ) + currentParameters
        }

        myFixture.checkResult(
            """
            import java.io.File

            class Foo {
                fun bar(@Anno(i = 8) file: File, @Anno(3) a: Int) {}
            }
            """.trimIndent(), true
        )
    }

    fun testReplaceInTheMiddle() {
        myFixture.configureByText(
            "foo.kt",
            """
            class Foo {
                fun ba<caret>r(@Anno(1) a: Int, @Anno(2) b: Int, @Anno(3) c: Int) {}
            }
            """.trimIndent()
        )

        runParametersTransformation("Change method parameters to '(a: Int, file: File, c: Int)'") { currentParameters ->
            ArrayList<ExpectedParameter>(currentParameters).apply {
                this[1] = expectedParameter(
                    PsiType.getTypeByName("java.io.File", project, myFixture.file.resolveScope), "file",
                    listOf(annotationRequest("Anno", intAttribute("i", 8)))
                )
            }
        }

        myFixture.checkResult(
            """
            import java.io.File

            class Foo {
                fun bar(@Anno(1) a: Int, @Anno(i = 8) file: File, @Anno(3) c: Int) {}
            }
            """.trimIndent(), true
        )
    }

    fun testChangeTypeWithComplexAnnotations() {
        myFixture.configureByText(
            "foo.kt",
            """
            class Foo {
                fun ba<caret>r(@Anno(1) @Root([Child("a"), Child("b")]) a: Int) {}
            }
            """.trimIndent()
        )

        runParametersTransformation("Change method parameters to '(a: Long)'") { currentParameters ->
            ArrayList<ExpectedParameter>(currentParameters).apply {
                this[0] = expectedParameter(PsiTypes.longType(), this[0].semanticNames.first(), this[0].expectedAnnotations)
            }
        }

        myFixture.checkResult(
            """
            class Foo {
                fun bar(@Anno(i = 1) @Root(children = [Child(s = "a"), Child(s = "b")]) a: Long) {}
            }
            """.trimIndent(), true
        )
    }

    private fun runParametersTransformation(
        actionName: String,
        transformation: (List<ChangeParametersRequest.ExistingParameterWrapper>) -> List<ExpectedParameter>
    ) {
        val psiMethod = myFixture.atCaret<UMethod>().javaPsi
        val currentParameters = psiMethod.parameters.map { ChangeParametersRequest.ExistingParameterWrapper(it) }
        myFixture.launchAction(
            createChangeParametersActions(
                psiMethod,
                SimpleChangeParametersRequest(
                    transformation(currentParameters)
                )
            ).findWithText(actionName)
        )
    }

}

private class SimpleChangeParametersRequest(private val list: List<ExpectedParameter>) : ChangeParametersRequest {
    override fun getExpectedParameters(): List<ExpectedParameter> = list

    override fun isValid(): Boolean = true
}