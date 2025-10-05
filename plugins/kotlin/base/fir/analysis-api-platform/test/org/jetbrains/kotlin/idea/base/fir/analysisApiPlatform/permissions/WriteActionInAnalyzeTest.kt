// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.permissions

import com.intellij.openapi.application.runWriteAction
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForProduction
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.junit.jupiter.api.assertThrows
import java.io.File

/**
 * This test ensures that the Analysis API behaves correctly when write actions are started inside [analyze] call.
 *
 * We test this on the IntelliJ side since the write/read action mechanisms in Analysis API tests are lacking proper implementation. We need
 * to test write action permissions against the real framework.
 */
@OptIn(KaAllowAnalysisOnEdt::class)
class WriteActionInAnalyzeTest : AbstractMultiModuleTest() {
    override fun getTestDataDirectory(): File = error("Should not be called")

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    // NOTE: Suspend calls are currently not supported in the Analysis API, so we're not testing the suspend endpoints for write actions.

    fun `test that write action inside 'analyze' is not allowed`() {
        val module = setupModule()

        assertWriteActionDenied {
            analyze(module) {
                runWriteAction { }
            }
        }
    }

    @OptIn(KaAllowAnalysisFromWriteAction::class)
    fun `test that write action inside 'analyze' is not allowed despite 'allowAnalysisFromWriteAction'`() {
        val module = setupModule()

        assertWriteActionDenied {
            allowAnalysisFromWriteAction {
                analyze(module) {
                    runWriteAction { }
                }
            }
        }
    }

    @OptIn(KaAllowAnalysisFromWriteAction::class)
    fun `test that write action inside 'analyze' is allowed with outer write action`() {
        val module = setupModule()

        assertWriteActionAllowed {
            runWriteAction {
                allowAnalysisFromWriteAction {
                    analyze(module) {
                        runWriteAction { }
                    }
                }
            }
        }
    }

    fun `test that write action inside nested 'analyze' is not allowed`() {
        val module1 = setupModule("module1")
        val module2 = setupModule("module2")

        assertWriteActionDenied {
            analyze(module1) {
                analyze(module2) {
                    runWriteAction { }
                }
            }
        }
    }

    @OptIn(KaAllowAnalysisFromWriteAction::class)
    fun `test that write action inside nested 'analyze' is not allowed despite 'allowAnalysisFromWriteAction'`() {
        val module1 = setupModule("module1")
        val module2 = setupModule("module2")

        assertWriteActionDenied {
            allowAnalysisFromWriteAction {
                analyze(module1) {
                    analyze(module2) {
                        runWriteAction { }
                    }
                }
            }
        }
    }

    @OptIn(KaAllowAnalysisFromWriteAction::class)
    fun `test that write action inside nested 'analyze' is allowed with outer write action`() {
        val module1 = setupModule("module1")
        val module2 = setupModule("module2")

        assertWriteActionAllowed {
            runWriteAction {
                allowAnalysisFromWriteAction {
                    analyze(module1) {
                        analyze(module2) {
                            runWriteAction { }
                        }
                    }
                }
            }
        }
    }

    private inline fun assertWriteActionDenied(crossinline action: () -> Unit) {
        allowAnalysisOnEdt {
            val exception = assertThrows<IllegalStateException> {
                action()
            }
            assertEquals(
                "Unexpected exception '${exception::class.simpleName}'.",
                "A write action should never be executed inside an analysis context (i.e. an `analyze` call).",
                exception.message,
            )
        }
    }

    private inline fun assertWriteActionAllowed(crossinline action: () -> Unit) {
        allowAnalysisOnEdt {
            action()
        }
    }

    private fun setupModule(name: String = "module"): KaModule {
        val module = createModuleInTmpDir(name)
        return module.toKaSourceModuleForProduction()!!
    }
}
