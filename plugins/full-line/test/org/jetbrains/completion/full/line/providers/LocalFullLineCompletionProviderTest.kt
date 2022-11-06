//package org.jetbrains.completion.full.line.providers
//
//import com.intellij.openapi.progress.EmptyProgressIndicator
//import com.intellij.openapi.progress.ProcessCanceledException
//import com.intellij.openapi.progress.ProgressIndicator
//import com.jetbrains.python.PythonLanguage
//import io.mockk.FunctionAnswer
//import io.mockk.every
//import io.mockk.spyk
//import org.jetbrains.completion.full.line.FullLineCompletionMode
//import org.jetbrains.completion.full.line.platform.FullLineCompletionQuery
//import org.jetbrains.completion.full.line.platform.tests.FullLineCompletionTestCase
//import org.jetbrains.completion.full.line.settings.state.MlServerCompletionAuthState
//import org.jetbrains.concurrency.runAsync
//import org.junit.jupiter.api.assertThrows
//import java.util.concurrent.Callable
//
//internal class IntelliJMLCompletionProviderTest : FullLineCompletionTestCase() {
//    companion object {
//        lateinit var provider: CloudFullLineCompletionProvider
//        lateinit var indicator: ProgressIndicator
//
//    }
//
//    override fun setUp() {
//        super.setUp()
//        MlServerCompletionAuthState.getInstance().state.authToken = "abc"
//        indicator = EmptyProgressIndicator()
//        provider = spyk()
//        every { provider.submitQuery(any()) } answers FunctionAnswer {
//            CloudFullLineCompletionProvider.executor.submit(Callable {
//                // Trojan horse :)
//                indicator.cancel()
//                Thread.sleep(5000)
//                emptyList()
//            })
//        }
//    }
//
//
//    fun `test canceled with pce`() {
//        val promise = runAsync {
//            provider.getVariants(
//                FullLineCompletionQuery(
//                    FullLineCompletionMode.FULL_LINE,
//                    "abc",
//                    "abc.py",
//                    "abc",
//                    0,
//                    PythonLanguage.INSTANCE,
//                    project,
//                    emptyList()
//                ),
//                indicator
//            )
//        }
//        assertThrows<ProcessCanceledException> {
//            promise.blockingGet(2000)
//        }
//    }
//}
