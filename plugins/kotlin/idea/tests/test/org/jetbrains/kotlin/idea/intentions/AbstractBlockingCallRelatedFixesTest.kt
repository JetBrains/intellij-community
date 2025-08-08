// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInspection.blockingCallsDetection.BlockingMethodInNonBlockingContextInspection
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import org.jetbrains.kotlin.build.joinToReadableString
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.junit.Assert

private val ktProjectDescriptor = object : KotlinWithJdkAndRuntimeLightProjectDescriptor(
    listOf(TestKotlinArtifacts.kotlinStdlib), listOf(TestKotlinArtifacts.kotlinStdlibSources)
) {
    override fun configureModule(module: Module, model: ModifiableRootModel) {
        super.configureModule(module, model)
        MavenDependencyUtil.addFromMaven(model, "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    }
}

abstract class AbstractBlockingCallRelatedFixesTest : KotlinLightCodeInsightFixtureTestCase() {
    abstract override val pluginMode: KotlinPluginMode
    
    override fun getProjectDescriptor(): LightProjectDescriptor {
        return ktProjectDescriptor
    }

    private lateinit var currentInspection: BlockingMethodInNonBlockingContextInspection

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "org/jetbrains/annotations/Blocking.java",
            """
                package org.jetbrains.annotations; 

                public @interface Blocking {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "org/jetbrains/annotations/NonBlocking.java",
            """
                package org.jetbrains.annotations; 

                public @interface NonBlocking {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "blockingMethod.kt",
            """
                import org.jetbrains.annotations.Blocking

                @Blocking fun block(): Int { return 42 }
            """.trimIndent()
        )
        currentInspection = BlockingMethodInNonBlockingContextInspection()
        myFixture.enableInspections(currentInspection)
    }

    open fun `test wrap in withContext`() {
        currentInspection.myConsiderUnknownContextBlocking = false

        myFixture.configureByText(
            "wrapInWithContext.kt",
            """                            
                suspend fun wrapWithContextFix() {
                    val variable = <warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">blo<caret>ck</warning>()
                    print(variable)
                }
            """.trimIndent()
        )
        myFixture.checkHighlighting()

        val action = myFixture.getAvailableIntention("Wrap call in 'withContext'")
        myFixture.launchAction(action!!)

        myFixture.checkResult(
            """
                import kotlinx.coroutines.Dispatchers
                import kotlinx.coroutines.withContext

                suspend fun wrapWithContextFix() {
                    val variable = withContext(Dispatchers.IO) {
                        block()
                    }
                    print(variable)
                }
            """.trimIndent()
        )
    }

    open fun `test add dispatcher in flow generator`() {
        myFixture.configureByText(
            "ioDispatcherInFlow.kt",
            """
            import kotlinx.coroutines.flow.flow

            fun flowFix() {
                flow<Int> { <warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">blo<caret>ck</warning>() }
            }
        """.trimIndent()
        )
        myFixture.checkHighlighting()

        val action = myFixture.getAvailableIntention("Flow on 'Dispatchers.IO'")
        myFixture.launchAction(action!!)

        myFixture.checkResult(
            """
                import kotlinx.coroutines.Dispatchers
                import kotlinx.coroutines.flow.flow
                import kotlinx.coroutines.flow.flowOn

                fun flowFix() {
                    flow<Int> { block() }
                        .flowOn(Dispatchers.IO)
                }
            """.trimIndent()
        )
    }

    open fun `test add dispatcher in flow`() {
        myFixture.configureByText(
            "ioDispatcherInFlow.kt",
            """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.flow.flowOf
            import kotlinx.coroutines.flow.flowOn
            import kotlinx.coroutines.flow.map

            fun flowFix() {
                flowOf(1,2,4)
                    .map { 
                        <warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">blo<caret>ck</warning>()
                    }
            }
        """.trimIndent()
        )
        myFixture.checkHighlighting()

        val action = myFixture.getAvailableIntention("Flow on 'Dispatchers.IO'")
        myFixture.launchAction(action!!)

        myFixture.checkResult(
            """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.flow.flowOf
            import kotlinx.coroutines.flow.flowOn
            import kotlinx.coroutines.flow.map

            fun flowFix() {
                flowOf(1, 2, 4)
                    .map {
                        block()
                    }
                    .flowOn(Dispatchers.IO)
            }
        """.trimIndent()
        )
    }

    open fun `test add flowOn to flow generator`() {
        myFixture.configureByText(
            "ioDispatcherInFlow.kt",
            """
            import kotlinx.coroutines.flow.flow

            fun flowFix() {
                flow<Int> { <warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">blo<caret>ck</warning>()}
            }
        """.trimIndent()
        )
        myFixture.checkHighlighting()

        val action = myFixture.getAvailableIntention("Flow on 'Dispatchers.IO'")
        myFixture.launchAction(action!!)

        myFixture.checkResult(
            """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.flow.flow
            import kotlinx.coroutines.flow.flowOn

            fun flowFix() {
                flow<Int> { block() }
                    .flowOn(Dispatchers.IO)
            }
        """.trimIndent()
        )
    }

    open fun `test replace unknown dispatcher in flow`() {
        myFixture.configureByText(
            "ioDispatcherInFlow.kt",
            """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.flow.flowOf
            import kotlinx.coroutines.flow.flowOn
            import kotlinx.coroutines.flow.map

            fun flowFix() {
                flowOf(1,2,4)
                    .map { 
                        <warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">blo<caret>ck</warning>()
                    }
                    .flowOn(Dispatchers.Main)
            }
        """.trimIndent()
        )
        myFixture.checkHighlighting()

        val action = myFixture.getAvailableIntention("Flow on 'Dispatchers.IO'")
        myFixture.launchAction(action!!)

        myFixture.checkResult(
            """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.flow.flowOf
            import kotlinx.coroutines.flow.flowOn
            import kotlinx.coroutines.flow.map

            fun flowFix() {
                flowOf(1,2,4)
                    .map { 
                        block()
                    }
                    .flowOn(Dispatchers.IO)
            }
        """.trimIndent()
        )
    }

    open fun `test replace unknown dispatcher in withContext`() {
        myFixture.configureByText(
            "ioDispatcherSwitch.kt",
            """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.withContext

            suspend fun switchContext() {
                withContext(Dispatchers.Main) {
                    <warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">blo<caret>ck</warning>()
                }
            }
        """.trimIndent()
        )
        myFixture.checkHighlighting()
        val action = myFixture.getAvailableIntention("Switch to 'Dispatchers.IO' context")

        myFixture.launchAction(action!!)
        myFixture.checkResult(
            """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.withContext

            suspend fun switchContext() {
                withContext(Dispatchers.IO) {
                    block()
                }
            }
        """.trimIndent()
        )
    }

    open fun `test wrap dot qualified expression`() {
        currentInspection.myConsiderUnknownContextBlocking = false

        myFixture.configureByText(
            "dotQualified.kt",
            """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.withContext
            import org.jetbrains.annotations.Blocking

            class Foo {
                @Blocking
                fun bar() {}
            }

            suspend fun wrapWithContextFix() {
                val variable = Foo().<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">ba<caret>r</warning>()
                print(variable)
            }
        """.trimIndent()
        )
        myFixture.checkHighlighting()
        val action = myFixture.getAvailableIntention("Wrap call in 'withContext'")

        myFixture.launchAction(action!!)
        myFixture.checkResult(
            """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.withContext
            import org.jetbrains.annotations.Blocking

            class Foo {
                @Blocking
                fun bar() {}
            }

            suspend fun wrapWithContextFix() {
                val variable = withContext(Dispatchers.IO) {
                    Foo().bar()
                }
                print(variable)
            }
        """.trimIndent()
        )
    }

    fun `test no significantly valuable fixes in non-suspendable context`() {
        myFixture.configureByText(
            "noIntentions.kt",
            """
            import kotlinx.coroutines.Dispatchers
            import kotlinx.coroutines.withContext
            import org.jetbrains.annotations.NonBlocking

            fun acceptSimpleBlock(block: () -> Unit) { block() }

            @NonBlocking
            fun differentContexts() {
                acceptSimpleBlock {
                    <warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">block</warning>()  
                }
            }
        """.trimIndent()
        )
        myFixture.checkHighlighting()
        val availableIntentions = myFixture.availableIntentions.map { it.familyName }
        Assert.assertTrue(availableIntentions.joinToReadableString(), availableIntentions.contains("Create test"))
    }

    fun `test consider unknown contexts blocking intention`() {
        val old = InspectionProfileImpl.INIT_INSPECTIONS
        InspectionProfileImpl.INIT_INSPECTIONS = true
        Disposer.register(testRootDisposable) { InspectionProfileImpl.INIT_INSPECTIONS = old }
        myFixture.allowTreeAccessForAllFiles()
        myFixture.configureByText(
            "UnknownContext.kt",
            """
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class CustomContext: CoroutineContext {
    override fun <R> fold(initial: R, operation: (R, CoroutineContext.Element) -> R): R = <info descr="TODO()" textAttributesKey="TODO_DEFAULT_ATTRIBUTES">TODO()</info>
    override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? = <info descr="TODO()" textAttributesKey="TODO_DEFAULT_ATTRIBUTES">TODO()</info>
    override fun minusKey(key: CoroutineContext.Key<*>): CoroutineContext = <info descr="TODO()" textAttributesKey="TODO_DEFAULT_ATTRIBUTES">TODO()</info>
}

suspend fun unknownContext() {
    withContext(CustomContext()) {
        <info descr="Consider unknown contexts non-blocking" textAttributesKey="INFORMATION_ATTRIBUTES">blo<caret>ck</info>()
    }
}        """.trimIndent()
        )
        myFixture.checkHighlighting(false, true, false)
        val action = myFixture.getAvailableIntention("Consider unknown contexts non-blocking")

        myFixture.launchAction(action!!)
        val warning = myFixture.doHighlighting(HighlightSeverity.WARNING)
            .firstOrNull { info ->
                info.description == "Possibly blocking call in non-blocking context could lead to thread starvation" && TextRange.create(info) == TextRange(511, 516)
            }

        Assert.assertNotNull("Inspection should report blocking call with unknown contexts considered non-blocking", warning)
        val reverseAction = myFixture.getAvailableIntention("Consider unknown contexts blocking")
        myFixture.launchAction(reverseAction!!)
        val info = myFixture.doHighlighting(HighlightSeverity.INFORMATION)
            .firstOrNull {
                it.description == "Consider unknown contexts non-blocking" && TextRange.create(it) == TextRange(511, 516)
            }
        Assert.assertNotNull("Inspection should NOT report blocking call with unknown contexts considered blocking," +
                                     " but should have intention to change behaviour instead", info)
    }
}
