// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.quickFix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.*
import com.intellij.lang.jvm.actions.*
import com.intellij.lang.jvm.actions.AnnotationAttributeValueRequest.NestedAnnotation
import com.intellij.lang.jvm.actions.AnnotationAttributeValueRequest.StringValue
import com.intellij.lang.jvm.types.JvmSubstitutor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair.pair
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCaseBase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UClass
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class K2CommonIntentionActionsTest : KotlinLightCodeInsightFixtureTestCaseBase() {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    private class SimpleMethodRequest(
        project: Project,
        private val methodName: String,
        private val modifiers: Collection<JvmModifier> = emptyList(),
        private val returnType: ExpectedTypes = emptyList(),
        private val annotations: Collection<AnnotationRequest> = emptyList(),
        parameters: List<ExpectedParameter> = emptyList(),
        private val targetSubstitutor: JvmSubstitutor = PsiJvmSubstitutor(project, PsiSubstitutor.EMPTY),
        private val elementToReplace: PsiElement? = null,
    ) : CreateMethodRequest {
        private val expectedParameters = parameters

        override fun getTargetSubstitutor(): JvmSubstitutor = targetSubstitutor

        override fun getModifiers() = modifiers

        override fun getMethodName() = methodName

        override fun getAnnotations() = annotations

        override fun getExpectedParameters(): List<ExpectedParameter> = expectedParameters

        override fun getReturnType() = returnType

        override fun isValid(): Boolean = true

        override fun getElementToReplace(): PsiElement? = elementToReplace
    }

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

    fun testMakeNotFinal() {
        myFixture.configureByText(
            "foo.kt", """
        class Foo {
            fun bar<caret>(){}
        }
        """
        )

        myFixture.launchAction(
            createModifierActions(
                myFixture.atCaret(), TestModifierRequest(JvmModifier.FINAL, false)
            ).findWithText("Make 'bar' 'open'")
        )
        myFixture.checkResult(
            """
        class Foo {
            open fun bar(){}
        }
        """
        )
    }

    fun testMakePrivate() {
        myFixture.configureByText(
            "foo.kt", """
        class Foo<caret> {
            fun bar(){}
        }
        """
        )

        myFixture.launchAction(
            createModifierActions(
                myFixture.atCaret(), TestModifierRequest(JvmModifier.PRIVATE, true)
            ).findWithText("Make 'Foo' 'private'")
        )
        myFixture.checkResult(
            """
        private class Foo {
            fun bar(){}
        }
        """
        )
    }

    fun testMakeNotPrivate() {
        myFixture.configureByText(
            "foo.kt", """
        private class Foo<caret> {
            fun bar(){}
        }
        """.trim()
        )

        myFixture.launchAction(
            createModifierActions(
                myFixture.atCaret(), TestModifierRequest(JvmModifier.PRIVATE, false)
            ).findWithText("Remove 'private' modifier")
        )
        myFixture.checkResult(
            """
        class Foo {
            fun bar(){}
        }
        """.trim(), true
        )
    }

    fun testMakePrivatePublic() {
        myFixture.configureByText(
            "foo.kt", """class Foo {
                        |    private fun <caret>bar(){}
                        |}""".trim().trimMargin()
        )

        myFixture.launchAction(
            createModifierActions(
                myFixture.atCaret(), TestModifierRequest(JvmModifier.PUBLIC, true)
            ).findWithText("Remove 'private' modifier")
        )
        myFixture.checkResult(
            """class Foo {
              |    fun <caret>bar(){}
              |}""".trim().trimMargin(), true
        )
    }

    fun testMakeProtectedPublic() {
        myFixture.configureByText(
            "foo.kt", """open class Foo {
                        |    protected fun <caret>bar(){}
                        |}""".trim().trimMargin()
        )

        myFixture.launchAction(
            createModifierActions(
                myFixture.atCaret(), TestModifierRequest(JvmModifier.PUBLIC, true)
            ).findWithText("Remove 'protected' modifier")
        )
        myFixture.checkResult(
            """open class Foo {
              |    fun <caret>bar(){}
              |}""".trim().trimMargin(), true
        )
    }

    fun testMakeInternalPublic() {
        myFixture.configureByText(
            "foo.kt", """class Foo {
                        |    internal fun <caret>bar(){}
                        |}""".trim().trimMargin()
        )

        myFixture.launchAction(
            createModifierActions(
                myFixture.atCaret(), TestModifierRequest(JvmModifier.PUBLIC, true)
            ).findWithText("Remove 'internal' modifier")
        )
        myFixture.checkResult(
            """class Foo {
              |    fun <caret>bar(){}
              |}""".trim().trimMargin(), true
        )
    }

    fun testAddAnnotation() {
        myFixture.configureByText(
            "foo.kt", """class Foo {
                        |   fun <caret>bar(){}
                        |}""".trim().trimMargin()
        )

        myFixture.launchAction(
            createAddAnnotationActions(
                myFixture.findElementByText("bar", KtModifierListOwner::class.java).toLightElements().single() as PsiMethod,
                annotationRequest("kotlin.jvm.JvmName", stringAttribute("name", "foo"))
            ).single()
        )
        myFixture.checkResult(
            """class Foo {
              |   @JvmName(name = "foo")
              |   fun <caret>bar(){}
              |}""".trim().trimMargin(), true
        )
    }

    fun testAddJavaAnnotationValue() {
        myFixture.addJavaFileToProject(
            "pkg/myannotation/JavaAnnotation.java", """
            package pkg.myannotation;

            public @interface JavaAnnotation {
                String value();
                int param() default 0;
            }
        """.trimIndent()
        )

        myFixture.configureByText(
            "foo.kt", """class Foo {
                        |   fun bar(){}
                        |   fun baz(){}
                        |}""".trim().trimMargin()
        )

        myFixture.launchAction(
            createAddAnnotationActions(
                myFixture.findElementByText("bar", KtModifierListOwner::class.java).toLightElements().single() as PsiMethod,
                annotationRequest("pkg.myannotation.JavaAnnotation", stringAttribute("value", "foo"), intAttribute("param", 2))
            ).single()
        )
        myFixture.launchAction(
            createAddAnnotationActions(
                myFixture.findElementByText("baz", KtModifierListOwner::class.java).toLightElements().single() as PsiMethod,
                annotationRequest("pkg.myannotation.JavaAnnotation", intAttribute("param", 2), stringAttribute("value", "foo"))
            ).single()
        )
        myFixture.checkResult(
            """import pkg.myannotation.JavaAnnotation
                |
                |class Foo {
                |   @JavaAnnotation("foo", param = 2)
                |   fun bar(){}
                |   @JavaAnnotation(param = 2, value = "foo")
                |   fun baz(){}
                |}""".trim().trimMargin(), true
        )
    }

    fun testAddJavaAnnotationArrayValue() {
        myFixture.addJavaFileToProject(
            "pkg/myannotation/JavaAnnotation.java", """
            package pkg.myannotation;

            public @interface JavaAnnotation {
                String[] value();
                int param() default 0;
            }
        """.trimIndent()
        )

        myFixture.configureByText(
            "foo.kt", """class Foo {
                        |   fun bar(){}
                        |   fun baz(){}
                        |}""".trim().trimMargin()
        )

        myFixture.launchAction(
            createAddAnnotationActions(
                myFixture.findElementByText("bar", KtModifierListOwner::class.java).toLightElements().single() as PsiMethod,
                annotationRequest(
                    "pkg.myannotation.JavaAnnotation",
                    arrayAttribute("value", listOf(StringValue("foo1"), StringValue("foo2"), StringValue("foo3"))),
                    intAttribute("param", 2)
                )
            ).single()
        )
        myFixture.launchAction(
            createAddAnnotationActions(
                myFixture.findElementByText("baz", KtModifierListOwner::class.java).toLightElements().single() as PsiMethod,
                annotationRequest(
                    "pkg.myannotation.JavaAnnotation",
                    intAttribute("param", 2),
                    arrayAttribute("value", listOf(StringValue("foo1"), StringValue("foo2"), StringValue("foo3")))
                )
            ).single()
        )
        myFixture.checkResult(
            """import pkg.myannotation.JavaAnnotation
                |
                |class Foo {
                |   @JavaAnnotation("foo1", "foo2", "foo3", param = 2)
                |   fun bar(){}
                |   @JavaAnnotation(param = 2, value = ["foo1", "foo2", "foo3"])
                |   fun baz(){}
                |}""".trim().trimMargin(), true
        )
    }

    fun testAddJavaAnnotationArrayValueWithNestedAnnotations() {
        myFixture.addJavaFileToProject(
            "pkg/myannotation/NestedJavaAnnotation.java", """
            package pkg.myannotation;

            public @interface NestedJavaAnnotation {
                String[] value();
                int nestedParam() default 0;
            }
        """.trimIndent()
        )

        myFixture.addJavaFileToProject(
            "pkg/myannotation/JavaAnnotation.java", """
            package pkg.myannotation;

            public @interface JavaAnnotation {
                NestedJavaAnnotation[] value();
                int param() default 0;
            }
        """.trimIndent()
        )

        myFixture.configureByText(
            "foo.kt", """class Foo {
                        |   fun bar(){}
                        |   fun baz(){}
                        |}""".trim().trimMargin()
        )

        myFixture.launchAction(
            createAddAnnotationActions(
                myFixture.findElementByText("bar", KtModifierListOwner::class.java).toLightElements().single() as PsiMethod,
                annotationRequest(
                    "pkg.myannotation.JavaAnnotation",
                    arrayAttribute(
                        "value", listOf(
                            NestedAnnotation(annotationRequest(
                                "pkg.myannotation.NestedJavaAnnotation",
                                arrayAttribute("value", listOf(StringValue("foo11"), StringValue("foo12"), StringValue("foo13"))),
                                intAttribute("nestedParam", 1)
                            )),
                            NestedAnnotation(annotationRequest(
                                "pkg.myannotation.NestedJavaAnnotation",
                                intAttribute("nestedParam", 2),
                                arrayAttribute("value", listOf(StringValue("foo21"), StringValue("foo22"), StringValue("foo23")))
                            )),
                        )
                    ),
                    intAttribute("param", 3)
                )
            ).single()
        )
        myFixture.launchAction(
            createAddAnnotationActions(
                myFixture.findElementByText("baz", KtModifierListOwner::class.java).toLightElements().single() as PsiMethod,
                annotationRequest(
                    "pkg.myannotation.JavaAnnotation",
                    intAttribute("param", 1),
                    arrayAttribute(
                        "value", listOf(
                            NestedAnnotation(annotationRequest(
                                "pkg.myannotation.NestedJavaAnnotation",
                                arrayAttribute("value", listOf(StringValue("foo11"), StringValue("foo12"), StringValue("foo13"))),
                                intAttribute("nestedParam", 2)
                            )),
                            NestedAnnotation(annotationRequest(
                                "pkg.myannotation.NestedJavaAnnotation",
                                intAttribute("nestedParam", 3),
                                arrayAttribute("value", listOf(StringValue("foo21"), StringValue("foo22"), StringValue("foo23")))
                            )),
                        )
                    )
                )
            ).single()
        )
        myFixture.checkResult(
            """import pkg.myannotation.JavaAnnotation
                |import pkg.myannotation.NestedJavaAnnotation
                |
                |class Foo {
                |   @JavaAnnotation(
                |       NestedJavaAnnotation("foo11", "foo12", "foo13", nestedParam = 1),
                |       NestedJavaAnnotation(nestedParam = 2, value = ["foo21", "foo22", "foo23"]),
                |       param = 3
                |   )
                |   fun bar(){}
                |   @JavaAnnotation(
                |       param = 1,
                |       value = [NestedJavaAnnotation("foo11", "foo12", "foo13", nestedParam = 2), NestedJavaAnnotation(
                |           nestedParam = 3,
                |           value = ["foo21", "foo22", "foo23"]
                |       )]
                |   )
                |   fun baz(){}
                |}""".trim().trimMargin(), true
        )
    }

    fun testAddJavaAnnotationOnFieldWithoutTarget() {
        myFixture.addJavaFileToProject(
            "pkg/myannotation/JavaAnnotation.java", """
            package pkg.myannotation;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            //no @Target
            @Retention(RetentionPolicy.RUNTIME)
            public @interface JavaAnnotation {
                String value();
                int param() default 0;
            }
        """.trimIndent()
        )

        myFixture.configureByText(
            "foo.kt", """class Foo {
                        |   val bar: String = null
                        |}""".trim().trimMargin()
        )

        myFixture.launchAction(
            createAddAnnotationActions(
                myFixture.findElementByText("bar", KtModifierListOwner::class.java).toLightElements().single { it is PsiField } as PsiField,
                annotationRequest("pkg.myannotation.JavaAnnotation")
            ).single()
        )

        myFixture.checkResult(
            """
                import pkg.myannotation.JavaAnnotation

                class Foo {
                   @field:JavaAnnotation
                   val bar: String = null
                }
                """.trimIndent(), true
        )

        TestCase.assertEquals(
            "SymbolLightAccessorMethod -> org.jetbrains.annotations.NotNull, SymbolLightFieldForProperty -> pkg.myannotation.JavaAnnotation, org.jetbrains.annotations.NotNull",
            annotationsString(myFixture.findElementByText("bar", KtModifierListOwner::class.java))
        )
    }


    fun testAddJavaAnnotationOnField() {
        myFixture.addJavaFileToProject(
            "pkg/myannotation/JavaAnnotation.java", """
            package pkg.myannotation;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
            @Retention(RetentionPolicy.RUNTIME)
            public @interface JavaAnnotation {
                String value();
                int param() default 0;
            }
        """.trimIndent()
        )

        myFixture.configureByText(
            "foo.kt", """class Foo {
                        |   val bar: String = null
                        |}""".trim().trimMargin()
        )

        myFixture.launchAction(
            createAddAnnotationActions(
                myFixture.findElementByText("bar", KtModifierListOwner::class.java).toLightElements().single { it is PsiField } as PsiField,
                annotationRequest("pkg.myannotation.JavaAnnotation")
            ).single()
        )

        myFixture.checkResult(
            """
                import pkg.myannotation.JavaAnnotation

                class Foo {
                   @JavaAnnotation
                   val bar: String = null
                }
                """.trimIndent(), true
        )

        TestCase.assertEquals(
            "SymbolLightAccessorMethod -> org.jetbrains.annotations.NotNull, SymbolLightFieldForProperty -> pkg.myannotation.JavaAnnotation, org.jetbrains.annotations.NotNull",
            annotationsString(myFixture.findElementByText("bar", KtModifierListOwner::class.java))
        )
    }

    fun testChangeAnnotationAttributeActionsWithJavaAnnotation() {
        myFixture.addJavaFileToProject(
            "pkg/myannotation/JavaAnnotation.java", """
            package pkg.myannotation;

            public @interface JavaAnnotation {
                String[] value();
                String description() default "";
            }
        """.trimIndent()
        )

        myFixture.configureByText(
            "foo.kt", """import pkg.myannotation.JavaAnnotation
                |
                |class Foo {
                |   @JavaAnnotation
                |   fun empty(){}
                |   @JavaAnnotation(description = "stay")
                |   fun emptyWithDescription(){}
                |   
                |   @JavaAnnotation("before")
                |   fun single(){}
                |   @JavaAnnotation(value = ["before"])
                |   fun singleExplicit(){}
                |   
                |   @JavaAnnotation("before", "before")
                |   fun multiple(){}
                |   @JavaAnnotation(value = ["before", "before", "before"])
                |   fun multipleExplicit(){}
                |   
                |   @JavaAnnotation("before", "before", description = "stay")
                |   fun multipleDescription(){}
                |   @JavaAnnotation(value = ["before", "before", "before"], description = "stay")
                |   fun multipleDescriptionExplicit(){}
                |}""".trim().trimMargin(),
        )

        myFixture.launchAction(
            createChangeAnnotationAttributeActions(
                myFixture.findMethod("empty").annotations.single(),
                0,
                arrayAttribute("value", listOf(StringValue("after1"), StringValue("after2"), StringValue("after3"))),
                "Change annotation attribute", "Change annotation attribute"
            ).single()
        )
        myFixture.launchAction(
            createChangeAnnotationAttributeActions(
                myFixture.findMethod("emptyWithDescription").annotations.single(),
                0,
                arrayAttribute("value", listOf(StringValue("after1"), StringValue("after2"), StringValue("after3"))),
                "Change annotation attribute", "Change annotation attribute"
            ).single()
        )

        myFixture.launchAction(
            createChangeAnnotationAttributeActions(
                myFixture.findMethod("single").annotations.single(),
                0,
                arrayAttribute("value", listOf(StringValue("after1"), StringValue("after2"), StringValue("after3"))),
                "Change annotation attribute", "Change annotation attribute"
            ).single()
        )
        myFixture.launchAction(
            createChangeAnnotationAttributeActions(
                myFixture.findMethod("singleExplicit").annotations.single(),
                0,
                arrayAttribute("value", listOf(StringValue("after1"), StringValue("after2"), StringValue("after3"))),
                "Change annotation attribute", "Change annotation attribute"
            ).single()
        )

        myFixture.launchAction(
            createChangeAnnotationAttributeActions(
                myFixture.findMethod("multiple").annotations.single(),
                0,
                arrayAttribute("value", listOf(StringValue("after1"), StringValue("after2"), StringValue("after3"))),
                "Change annotation attribute", "Change annotation attribute"
            ).single()
        )

        myFixture.launchAction(
            createChangeAnnotationAttributeActions(
                myFixture.findMethod("multipleExplicit").annotations.single(),
                0,
                arrayAttribute("value", listOf(StringValue("during"), StringValue("during"), StringValue("during"))),
                "Change annotation attribute", "Change annotation attribute"
            ).single()
        )

        myFixture.launchAction(
            createChangeAnnotationAttributeActions(
                myFixture.findMethod("multipleExplicit").annotations.single(),
                0,
                arrayAttribute("value", listOf(StringValue("after1"), StringValue("after2"))),
                "Change annotation attribute", "Change annotation attribute"
            ).single()
        )

        myFixture.launchAction(
            createChangeAnnotationAttributeActions(
                myFixture.findMethod("multipleDescription").annotations.single(),
                0,
                arrayAttribute("value", listOf(StringValue("after1"), StringValue("after2"), StringValue("after3"))),
                "Change annotation attribute", "Change annotation attribute"
            ).single()
        )

        myFixture.launchAction(
            createChangeAnnotationAttributeActions(
                myFixture.findMethod("multipleDescriptionExplicit").annotations.single(),
                0,
                arrayAttribute("value", listOf(StringValue("during"), StringValue("during"), StringValue("during"))),
                "Change annotation attribute", "Change annotation attribute"
            ).single()
        )
        myFixture.launchAction(
            createChangeAnnotationAttributeActions(
                myFixture.findMethod("multipleDescriptionExplicit").annotations.single(),
                0,
                arrayAttribute("value", listOf(StringValue("after1"), StringValue("after2"))),
                "Change annotation attribute", "Change annotation attribute"
            ).single()
        )

        myFixture.checkResult(
            """import pkg.myannotation.JavaAnnotation
                |
                |class Foo {
                |   @JavaAnnotation("after1", "after2", "after3")
                |   fun empty(){}
                |   @JavaAnnotation("after1", "after2", "after3", description = "stay")
                |   fun emptyWithDescription(){}
                |   
                |   @JavaAnnotation("after1", "after2", "after3")
                |   fun single(){}
                |   @JavaAnnotation("after1", "after2", "after3")
                |   fun singleExplicit(){}
                |   
                |   @JavaAnnotation("after1", "after2", "after3")
                |   fun multiple(){}
                |   @JavaAnnotation("after1", "after2")
                |   fun multipleExplicit(){}
                |   
                |   @JavaAnnotation("after1", "after2", "after3", description = "stay")
                |   fun multipleDescription(){}
                |   @JavaAnnotation("after1", "after2", description = "stay")
                |   fun multipleDescriptionExplicit(){}
                |}""".trim().trimMargin(), true
        )
    }

    fun testChangeAnnotationAttributeActionsWithKotlinAnnotation() {
        myFixture.addKotlinFileToProject(
            "pkg/myannotation/KotlinAnnotation.kt", """
            package pkg.myannotation

            annotation class KotlinAnnotation(
                val value: Array<String>
            )
        """.trimIndent()
        )

        myFixture.configureByText(
            "foo.kt", """import pkg.myannotation.KotlinAnnotation
                |
                |class Foo {
                |   @KotlinAnnotation(["foo1"])
                |   fun single(){}
                |   @KotlinAnnotation(value = ["foo1"])
                |   fun singleExplicit(){}
                |   @KotlinAnnotation(["foo1", "foo2"])
                |   fun multiple(){}
                |   @KotlinAnnotation(value = ["foo1", "foo2", "foo3"])
                |   fun multipleExplicit(){}
                |}""".trim().trimMargin(),
        )

        myFixture.launchAction(
            createChangeAnnotationAttributeActions(
                myFixture.findMethod("single").annotations.single(),
                0,
                arrayAttribute("value", listOf(StringValue("foo1"), StringValue("foo2"), StringValue("foo3"))),
                "Change 'value' attribute of 'JavaAnnotation' annotation",
                "Change annotation attribute"
            ).single()
        )
        myFixture.launchAction(
            createChangeAnnotationAttributeActions(
                myFixture.findMethod("singleExplicit").annotations.single(),
                0,
                arrayAttribute("value", listOf(StringValue("foo1"), StringValue("foo2"), StringValue("foo3"))),
                "Change 'value' attribute of 'JavaAnnotation' annotation",
                "Change annotation attribute"
            ).single()
        )

        myFixture.launchAction(
            createChangeAnnotationAttributeActions(
                myFixture.findMethod("multiple").annotations.single(),
                0,
                arrayAttribute("value", listOf(StringValue("foo1"), StringValue("foo2"), StringValue("foo3"))),
                "Change 'value' attribute of 'JavaAnnotation' annotation",
                "Change annotation attribute"
            ).single()
        )

        myFixture.launchAction(
            createChangeAnnotationAttributeActions(
                myFixture.findMethod("multipleExplicit").annotations.single(),
                0,
                arrayAttribute("value", listOf(StringValue("foo1"), StringValue("foo2"))),
                "Change 'value' attribute of 'JavaAnnotation' annotation",
                "Change annotation attribute"
            ).single()
        )

        myFixture.launchAction(
            createChangeAnnotationAttributeActions(
                myFixture.findMethod("multipleExplicit").annotations.single(),
                0,
                arrayAttribute("value", listOf(StringValue("foo1"), StringValue("foo2"), StringValue("foo3"))),
                "Change 'value' attribute of 'JavaAnnotation' annotation",
                "Change annotation attribute"
            ).single()
        )

        myFixture.checkResult(
            """import pkg.myannotation.KotlinAnnotation
                |
                |class Foo {
                |   @KotlinAnnotation(value = ["foo1", "foo2", "foo3"])
                |   fun single(){}
                |   @KotlinAnnotation(value = ["foo1", "foo2", "foo3"])
                |   fun singleExplicit(){}
                |   @KotlinAnnotation(value = ["foo1", "foo2", "foo3"])
                |   fun multiple(){}
                |   @KotlinAnnotation(value = ["foo1", "foo2", "foo3"])
                |   fun multipleExplicit(){}
                |}""".trim().trimMargin(), true
        )
    }

    fun testChangeMethodType() {
        myFixture.configureByText(
            "foo.kt", """class Foo {
                        |   fun <caret>bar(){}
                        |}""".trim().trimMargin()
        )

        val method = myFixture.findElementByText("bar", KtNamedFunction::class.java).toLightElements().single() as JvmMethod
        val typeRequest = typeRequest("String", emptyList())
        myFixture.launchAction(createChangeTypeActions(method, typeRequest).single())

        myFixture.checkResult(
            """class Foo {
              |   fun <caret>bar(): String {}
              |}""".trim().trimMargin(), true
        )
    }

    fun testChangeMethodTypeToTypeWithAnnotations() {
        myFixture.configureByText(
            "foo.kt", """class Foo {
                        |   fun <caret>bar(){}
                        |}""".trim().trimMargin()
        )

        myFixture.addKotlinFileToProject(
            "pkg/myannotation/annotations.kt", """
            package pkg.myannotation

            @Target(AnnotationTarget.TYPE)
            annotation class MyAnno
        """.trimIndent()
        )

        val method = myFixture.findElementByText("bar", KtNamedFunction::class.java).toLightElements().single() as JvmMethod
        val typeRequest = typeRequest("String", listOf(annotationRequest("pkg.myannotation.MyAnno")))
        myFixture.launchAction(createChangeTypeActions(method, typeRequest).single())

        myFixture.checkResult(
            """
              |import pkg.myannotation.MyAnno
              |
              |class Foo {
              |   fun <caret>bar(): @MyAnno String {}
              |}""".trim().trimMargin(), true
        )
    }

    fun testChangeMethodTypeRemoveAnnotations() {
        myFixture.addKotlinFileToProject(
            "pkg/myannotation/annotations.kt", """
            package pkg.myannotation

            @Target(AnnotationTarget.TYPE)
            annotation class MyAnno
        """.trimIndent()
        )

        myFixture.configureByText(
            "foo.kt", """
                |import pkg.myannotation.MyAnno
                |
                |class Foo {
                |   fun <caret>bar(): @MyAnno String {}
                |}""".trim().trimMargin()
        )

        val method = myFixture.findElementByText("bar", KtNamedFunction::class.java).toLightElements().single() as JvmMethod
        val typeRequest = typeRequest(null, emptyList())
        myFixture.launchAction(createChangeTypeActions(method, typeRequest).single())

        myFixture.checkResult(
            """
              |import pkg.myannotation.MyAnno
              |
              |class Foo {
              |   fun <caret>bar(): String {}
              |}""".trim().trimMargin(), true
        )
    }

    fun testChangeMethodTypeChangeAnnotationsOnly() {
        myFixture.addKotlinFileToProject(
            "pkg/myannotation/annotations.kt", """
            package pkg.myannotation

            @Target(AnnotationTarget.TYPE)
            annotation class MyAnno
            
            @Target(AnnotationTarget.TYPE)
            annotation class MyOtherAnno
        """.trimIndent()
        )

        myFixture.configureByText(
            "foo.kt", """
                |import pkg.myannotation.MyAnno
                |
                |class Foo {
                |   fun <caret>bar(): @MyAnno String {}
                |}""".trim().trimMargin()
        )

        val method = myFixture.findElementByText("bar", KtNamedFunction::class.java).toLightElements().single() as JvmMethod
        val typeRequest = typeRequest(null, listOf(annotationRequest("pkg.myannotation.MyOtherAnno")))
        myFixture.launchAction(createChangeTypeActions(method, typeRequest).single())

        myFixture.checkResult(
            """
              |import pkg.myannotation.MyAnno
              |import pkg.myannotation.MyOtherAnno
              |
              |class Foo {
              |   fun <caret>bar(): @MyOtherAnno String {}
              |}""".trim().trimMargin(), true
        )
    }

    fun testChangeMethodTypeAddJavaAnnotation() {
        myFixture.addJavaFileToProject(
            "pkg/myannotation/JavaAnnotation.java", """
            package pkg.myannotation;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;

            @Target(ElementType.TYPE)
            public @interface JavaAnnotation {}
        """.trimIndent()
        )

        myFixture.addKotlinFileToProject(
            "pkg/myannotation/annotations.kt", """
            package pkg.myannotation

            @Target(AnnotationTarget.TYPE)
            annotation class MyOtherAnno
        """.trimIndent()
        )

        myFixture.configureByText(
            "foo.kt", """
                |import pkg.myannotation.MyOtherAnno
                |
                |class Foo {
                |   fun <caret>bar(): @MyOtherAnno String {}
                |}""".trim().trimMargin()
        )

        val method = myFixture.findElementByText("bar", KtNamedFunction::class.java).toLightElements().single() as JvmMethod
        val typeRequest = typeRequest(
            null,
            listOf(annotationRequest("pkg.myannotation.JavaAnnotation"), annotationRequest("pkg.myannotation.MyOtherAnno"))
        )
        myFixture.launchAction(createChangeTypeActions(method, typeRequest).single())

        myFixture.checkResult(
            """
              |import pkg.myannotation.JavaAnnotation
              |import pkg.myannotation.MyOtherAnno
              |
              |class Foo {
              |   fun <caret>bar(): @JavaAnnotation @MyOtherAnno String {}
              |}""".trim().trimMargin(), true
        )
    }

    fun testChangeMethodTypeWithComments() {
        myFixture.addKotlinFileToProject(
            "pkg/myannotation/annotations.kt", """
            package pkg.myannotation

            @Target(AnnotationTarget.TYPE)
            annotation class MyAnno
            
            @Target(AnnotationTarget.TYPE)
            annotation class MyOtherAnno
        """.trimIndent()
        )

        myFixture.configureByText(
            "foo.kt", """
                |import pkg.myannotation.MyOtherAnno
                |
                |class Foo {
                |   fun <caret>bar(): @MyOtherAnno /*1*/ String {}
                |}""".trim().trimMargin()
        )

        val method = myFixture.findElementByText("bar", KtNamedFunction::class.java).toLightElements().single() as JvmMethod
        val typeRequest = typeRequest(null, listOf(annotationRequest("pkg.myannotation.MyAnno")))
        myFixture.launchAction(createChangeTypeActions(method, typeRequest).single())

        myFixture.checkResult(
            """
              |import pkg.myannotation.MyAnno
              |import pkg.myannotation.MyOtherAnno
              |
              |class Foo {
              |   fun <caret>bar(): /*1*/@MyAnno String {}
              |}""".trim().trimMargin(), true
        )
    }

    fun testChangeMethodTypeToJavaType() {
        myFixture.addJavaFileToProject(
            "pkg/mytype/MyType.java", """
            package pkg.mytype;

            public class MyType {}
        """.trimIndent()
        )

        myFixture.configureByText(
            "foo.kt", """
                |class Foo {
                |   fun <caret>bar() {}
                |}""".trim().trimMargin()
        )

        val method = myFixture.findElementByText("bar", KtNamedFunction::class.java).toLightElements().single() as JvmMethod
        val typeRequest = typeRequest("pkg.mytype.MyType", emptyList())
        myFixture.launchAction(createChangeTypeActions(method, typeRequest).single())

        myFixture.checkResult(
            """
              |import pkg.mytype.MyType
              |
              |class Foo {
              |   fun <caret>bar(): MyType {}
              |}""".trim().trimMargin(), true
        )
    }

    private fun annotationsString(findElementByText: KtModifierListOwner) = findElementByText.toLightElements()
        .joinToString { elem ->
            "${elem.javaClass.simpleName} -> ${(elem as PsiModifierListOwner).annotations.mapNotNull { it.qualifiedName }.joinToString()}"
        }

    fun testDontMakePublicPublic() {
        myFixture.configureByText(
            "foo.kt", """class Foo {
                        |    fun <caret>bar(){}
                        |}""".trim().trimMargin()
        )

        assertEmpty(createModifierActions(myFixture.atCaret(), TestModifierRequest(JvmModifier.PUBLIC, true)))
    }

    fun testDontMakeFunInObjectsOpen() {
        myFixture.configureByText(
            "foo.kt", """
        object Foo {
            fun bar<caret>(){}
        }
        """.trim()
        )
        assertEmpty(createModifierActions(myFixture.atCaret(), TestModifierRequest(JvmModifier.FINAL, false)))
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun _testAddVoidVoidMethod() {
        myFixture.configureByText(
            "foo.kt", """
        |class Foo<caret> {
        |    fun bar() {}
        |}
        """.trim().trimMargin()
        )

        myFixture.launchAction(
            allowAnalysisOnEdt {
                createMethodActions(
                    myFixture.atCaret(),
                    methodRequest(project, "baz", listOf(JvmModifier.PRIVATE), PsiTypes.voidType())
                ).findWithText("Add method 'baz' to 'Foo'")
            }
        )
        myFixture.checkResult(
            """
        |class Foo {
        |    fun bar() {}
        |    private fun baz() {
        |
        |    }
        |}
        """.trim().trimMargin(), true
        )
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun testAddIntIntMethod() {
        myFixture.configureByText(
            "foo.kt", """
        |class Foo<caret> {
        |    fun bar() {}
        |}
        """.trim().trimMargin()
        )

        myFixture.launchAction(
            allowAnalysisOnEdt {
                createMethodActions(
                    myFixture.atCaret(),
                    SimpleMethodRequest(
                        project,
                        methodName = "baz",
                        modifiers = listOf(JvmModifier.PUBLIC),
                        returnType = expectedTypes(PsiTypes.intType()),
                        parameters = expectedParams(PsiTypes.intType())
                    )
                ).findWithText("Add method 'baz' to 'Foo'")
            }
        )
        myFixture.checkResult(
            """
        |class Foo {
        |    fun bar() {}
        |    fun baz(param0: Int): Int {
        |        TODO("Not yet implemented")
        |    }
        |}
        """.trim().trimMargin(), true
        )
    }

    fun _testAddIntPrimaryConstructor() {
        myFixture.configureByText(
            "foo.kt", """
        |class Foo<caret> {
        |}
        """.trim().trimMargin()
        )

        myFixture.launchAction(
            createConstructorActions(
                myFixture.atCaret(), constructorRequest(project, listOf(pair("param0", PsiTypes.intType() as PsiType)))
            ).findWithText("Add primary constructor to 'Foo'")
        )
        myFixture.checkResult(
            """
        |class Foo(param0: Int) {
        |}
        """.trim().trimMargin(), true
        )
    }

    fun _testAddIntSecondaryConstructor() {
        myFixture.configureByText(
            "foo.kt", """
        |class <caret>Foo() {
        |}
        """.trim().trimMargin()
        )

        myFixture.launchAction(
            createConstructorActions(
                myFixture.atCaret(),
                constructorRequest(project, listOf(pair("param0", PsiTypes.intType() as PsiType)))
            ).findWithText("Add secondary constructor to 'Foo'")
        )
        myFixture.checkResult(
            """
        |class Foo() {
        |    constructor(param0: Int) {
        |
        |    }
        |}
        """.trim().trimMargin(), true
        )
    }

    fun testChangePrimaryConstructorInt() {
        myFixture.configureByText(
            "foo.kt", """
        |class <caret>Foo() {
        |}
        """.trim().trimMargin()
        )

        myFixture.launchAction(
            createConstructorActions(
                myFixture.atCaret(),
                constructorRequest(project, listOf(pair("param0", PsiTypes.intType() as PsiType)))
            ).findWithText("Add 'int' as 1st parameter to constructor 'Foo'")
        )
        myFixture.checkResult(
            """
        |class Foo(param0: Int) {
        |}
        """.trim().trimMargin(), true
        )
    }

    fun testRemoveConstructorParameters() {
        myFixture.configureByText(
            "foo.kt", """
        |class <caret>Foo(i: Int) {
        |}
        """.trim().trimMargin()
        )

        myFixture.launchAction(
            createConstructorActions(
                myFixture.atCaret(),
                constructorRequest(project, emptyList())
            ).findWithText("Remove 1st parameter from constructor 'Foo'")
        )
        myFixture.checkResult(
            """
        |class Foo() {
        |}
        """.trim().trimMargin(), true
        )
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun testAddStringVarProperty() {
        myFixture.configureByText(
            "foo.kt", """
        |class Foo<caret> {
        |    fun bar() {}
        |}
        """.trim().trimMargin()
        )

        myFixture.launchAction(
            allowAnalysisOnEdt {
                createMethodActions(
                    myFixture.atCaret(),
                    SimpleMethodRequest(
                        project,
                        methodName = "setBaz",
                        modifiers = listOf(JvmModifier.PUBLIC),
                        returnType = expectedTypes(),
                        parameters = expectedParams(PsiType.getTypeByName("java.lang.String", project, project.allScope()))
                    )
                ).findWithText("Add 'var' property 'baz' to 'Foo'")
            }
        )
        myFixture.checkResult(
            """
        |class Foo {
        |    var baz: String = TODO("initialize me")
        |
        |    fun bar() {}
        |}
        """.trim().trimMargin(), true
        )
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun testAddLateInitStringVarProperty() {
        myFixture.configureByText(
            "foo.kt", """
        |class Foo<caret> {
        |    fun bar() {}
        |}
        """.trim().trimMargin()
        )

        myFixture.launchAction(
            allowAnalysisOnEdt {
                createMethodActions(
                    myFixture.atCaret(),
                    SimpleMethodRequest(
                        project,
                        methodName = "setBaz",
                        modifiers = listOf(JvmModifier.PUBLIC),
                        returnType = expectedTypes(),
                        parameters = expectedParams(PsiType.getTypeByName("java.lang.String", project, project.allScope()))
                    )
                ).findWithText("Add 'lateinit var' property 'baz' to 'Foo'")
            }
        )
        myFixture.checkResult(
            """
        |class Foo {
        |    lateinit var baz: String
        |
        |    fun bar() {}
        |}
        """.trim().trimMargin(), true
        )
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun _testAddStringVarField() {
        myFixture.configureByText(
            "foo.kt", """
        |class Foo<caret> {
        |    fun bar() {}
        |}
        """.trim().trimMargin()
        )
        myFixture.launchAction(
            allowAnalysisOnEdt {
                createFieldActions(
                    myFixture.atCaret(),
                    FieldRequest(project, emptyList(), "java.util.Date", "baz")
                ).findWithText("Add 'var' property 'baz' to 'Foo'")
            }
        )
        myFixture.checkResult(
            """
        |import java.util.Date
        |
        |class Foo {
        |    @JvmField
        |    var baz: Date = TODO("initialize me")
        |
        |    fun bar() {}
        |}
        """.trim().trimMargin(), true
        )
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun _testAddLateInitStringVarField() {
        myFixture.configureByText(
            "foo.kt", """
        |class Foo<caret> {
        |    fun bar() {}
        |}
        """.trim().trimMargin()
        )

        myFixture.launchAction(
            allowAnalysisOnEdt {
                createFieldActions(
                    myFixture.atCaret(),
                    FieldRequest(project, listOf(JvmModifier.PRIVATE), "java.lang.String", "baz")
                ).findWithText("Add private 'lateinit var' property 'baz' to 'Foo'")
            }
        )
        myFixture.checkResult(
            """
        |class Foo {
        |    private lateinit var baz: String
        |
        |    fun bar() {}
        |}
        """.trim().trimMargin(), true
        )
    }


    private fun createFieldActions(atCaret: JvmClass, fieldRequest: CreateFieldRequest): List<IntentionAction> =
        EP_NAME.extensions.flatMap { it.createAddFieldActions(atCaret, fieldRequest) }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun _testAddStringValProperty() {
        myFixture.configureByText(
            "foo.kt", """
        |class Foo<caret> {
        |    fun bar() {}
        |}
        """.trim().trimMargin()
        )

        myFixture.launchAction(
            allowAnalysisOnEdt {
                createMethodActions(
                    myFixture.atCaret(),
                    SimpleMethodRequest(
                        project,
                        methodName = "getBaz",
                        modifiers = listOf(JvmModifier.PUBLIC),
                        returnType = expectedTypes(PsiType.getTypeByName("java.lang.String", project, project.allScope())),
                        parameters = expectedParams()
                    )
                ).findWithText("Add 'val' property 'baz' to 'Foo'")
            }
        )
        myFixture.checkResult(
            """
        |class Foo {
        |    val baz: String = TODO("initialize me")
        |
        |    fun bar() {}
        |}
        """.trim().trimMargin(), true
        )
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun testGetMethodHasParameters() {
        myFixture.configureByText(
            "foo.kt", """
        |class Foo<caret> {
        |    fun bar() {}
        |}
        """.trim().trimMargin()
        )

        myFixture.launchAction(
            allowAnalysisOnEdt {
                createMethodActions(
                    myFixture.atCaret(),
                    SimpleMethodRequest(
                        project,
                        methodName = "getBaz",
                        modifiers = listOf(JvmModifier.PUBLIC),
                        returnType = expectedTypes(PsiType.getTypeByName("java.lang.String", project, project.allScope())),
                        parameters = expectedParams(PsiType.getTypeByName("java.lang.String", project, project.allScope()))
                    )
                ).findWithText("Add method 'getBaz' to 'Foo'")
            }
        )
        myFixture.checkResult(
            """
        |class Foo {
        |    fun bar() {}
        |    fun getBaz(param0: String): String {
        |        TODO("Not yet implemented")
        |    }
        |}
        """.trim().trimMargin(), true
        )
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun testSetMethodHasStringReturnType() {
        myFixture.configureByText(
            "foo.kt", """
        |class Foo<caret> {
        |    fun bar() {}
        |}
        """.trim().trimMargin()
        )

        myFixture.launchAction(
            allowAnalysisOnEdt {
                createMethodActions(
                    myFixture.atCaret(),
                    SimpleMethodRequest(
                        project,
                        methodName = "setBaz",
                        modifiers = listOf(JvmModifier.PUBLIC),
                        returnType = expectedTypes(PsiType.getTypeByName("java.lang.String", project, project.allScope())),
                        parameters = expectedParams(PsiType.getTypeByName("java.lang.String", project, project.allScope()))
                    )
                ).findWithText("Add method 'setBaz' to 'Foo'")
            }
        )
        myFixture.checkResult(
            """
        |class Foo {
        |    fun bar() {}
        |    fun setBaz(param0: String): String {
        |        TODO("Not yet implemented")
        |    }
        |}
        """.trim().trimMargin(), true
        )
    }


    @OptIn(KaAllowAnalysisOnEdt::class)
    fun _testSetMethodHasTwoParameters() {
        myFixture.configureByText(
            "foo.kt", """
        |class Foo<caret> {
        |    fun bar() {}
        |}
        """.trim().trimMargin()
        )

        myFixture.launchAction(
            allowAnalysisOnEdt {
                createMethodActions(
                    myFixture.atCaret(),
                    SimpleMethodRequest(
                        project,
                        methodName = "setBaz",
                        modifiers = listOf(JvmModifier.PUBLIC),
                        returnType = expectedTypes(PsiTypes.voidType()),
                        parameters = expectedParams(
                            PsiType.getTypeByName("java.lang.String", project, project.allScope()),
                            PsiType.getTypeByName("java.lang.String", project, project.allScope())
                        )
                    )
                ).findWithText("Add method 'setBaz' to 'Foo'")
            }
        )
        myFixture.checkResult(
            """
        |class Foo {
        |    fun bar() {}
        |    fun setBaz(param0: String, param1: String) {
        |
        |    }
        |}
        """.trim().trimMargin(), true
        )
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun testReplaceRefByIntMethod() {
        val file = myFixture.configureByText(
            "foo.kt", """
        |class Foo {
        |    bar<caret>
        |}
        """.trim().trimMargin()
        )

        val psiReference = file.findElementAt(myFixture.caretOffset - 1)
        val targetClass = PsiTreeUtil.getParentOfType(psiReference, KtClass::class.java)
        assertNotNull(targetClass)

        allowAnalysisOnEdt {
            createMethodActions(
                targetClass.toUElementOfType<UClass>()!!,
                SimpleMethodRequest(
                    project,
                    methodName = "baz",
                    modifiers = listOf(JvmModifier.PUBLIC),
                    returnType = expectedTypes(PsiTypes.intType()),
                    parameters = expectedParams(PsiTypes.intType()),
                    elementToReplace = psiReference
                )
            ).first()
        }.apply {
            runInEdtAndWait {
                WriteCommandAction.runWriteCommandAction(project) {
                    invoke(project, myFixture.editor, myFixture.file)
                }
            }
        }
        myFixture.checkResult(
            """
        |class Foo {      
        |    fun baz(param0: Int): Int {
        |        TODO("Not yet implemented")
        |    }
        |}
        """.trim().trimMargin(), true
        )
    }


    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun List<IntentionAction>.findWithText(text: String): IntentionAction =
        this.filter { it.isAvailable(myFixture.project, myFixture.editor, myFixture.file) }.firstOrNull { it.text == text }
            ?: Assert.fail("intention with text '$text' was not found, only ${this.joinToString { "\"${it.text}\"" }} available") as Nothing

    private fun CodeInsightTestFixture.addJavaFileToProject(relativePath: String, @Language("JAVA") fileText: String) =
        this.addFileToProject(relativePath, fileText)

    private fun CodeInsightTestFixture.addKotlinFileToProject(relativePath: String, @Language("kotlin") fileText: String) =
        this.addFileToProject(relativePath, fileText)

    private fun expectedTypes(vararg psiTypes: PsiType) = psiTypes.map { expectedType(it) }

    private fun expectedParams(vararg psyTypes: PsiType) =
        psyTypes.mapIndexed { index, psiType -> expectedParameter(expectedTypes(psiType), "param$index") }

    private fun CodeInsightTestFixture.findMethod(name: String): JvmMethod =
        this.findElementByText(name, KtModifierListOwner::class.java).toLightElements().single() as JvmMethod

    class FieldRequest(
        private val project: Project,
        val modifiers: List<JvmModifier>,
        val type: String,
        val name: String
    ) : CreateFieldRequest {
        override fun getTargetSubstitutor(): JvmSubstitutor = PsiJvmSubstitutor(project, PsiSubstitutor.EMPTY)

        override fun getAnnotations(): Collection<AnnotationRequest> = emptyList()

        override fun getModifiers(): Collection<JvmModifier> = modifiers

        override fun isConstant(): Boolean = false

        override fun getFieldType(): List<ExpectedType> =
            expectedTypes(PsiType.getTypeByName(type, project, project.allScope()))

        override fun getFieldName(): String = name

        override fun isValid(): Boolean = true

        override fun getInitializer(): JvmValue? = null
    }

}

internal inline fun <reified T : JvmElement> CodeInsightTestFixture.atCaret() = elementAtCaret.toUElement() as T

private class TestModifierRequest(private val _modifier: JvmModifier, private val shouldBePresent: Boolean) : ChangeModifierRequest {
    override fun shouldBePresent(): Boolean = shouldBePresent
    override fun isValid(): Boolean = true
    override fun getModifier(): JvmModifier = _modifier
}


