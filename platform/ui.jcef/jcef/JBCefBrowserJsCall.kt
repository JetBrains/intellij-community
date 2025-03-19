// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.jcef.JBCefJSQuery.create
import kotlinx.coroutines.*
import org.intellij.lang.annotations.Language
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private typealias JsExpression = String
private typealias JsExpressionResult = String?
private typealias JsExpressionResultPromise = AsyncPromise<JsExpressionResult>

class JBCefBrowserJsCallError(message: String) : IllegalStateException("Failed to execute JavaScript expression in JCEF browser. $message")

/**
 * Asynchronously runs JavaScript code in the JCEF browser.
 *
 * @param javaScriptExpression
 * The passed JavaScript code should be either:
 * * a valid single-line JavaScript expression
 * * a valid multi-line function-body with at least one "return" statement
 *
 * Examples:
 * ```Kotlin
 *  browser.executeJavaScriptAsync("2 + 2")
 *     .onSuccess { r -> r /* r is 4 */ }
 *
 *  browser.executeJavaScriptAsync("return 2 + 2")
 *     .onSuccess { r -> r /* r is 4 */ }
 *
 *  browser.executeJavaScriptAsync("""
 *        function sum(s1, s2) {
 *            return s1 + s2;
 *        };
 *
 *        return sum(2,2);
 *  """.trimIndent())
 *     .onSuccess { r -> r /* r is 4 */ }
 *
 * ```
 *
 * @return The [Promise] that provides JS execution result or an error.
 */
@Deprecated(message = "Use JBCefBrowser.executeJavaScript(JsExpression) and coroutines instead",
            replaceWith = ReplaceWith("browser.executeJavaScript", "com.intellij.ui.jcef.executeJavaScriptAsync"))
fun JBCefBrowser.executeJavaScriptAsync(@Language("JavaScript") javaScriptExpression: JsExpression): Promise<JsExpressionResult> =
  @Suppress("DEPRECATION")
  JBCefBrowserJsCall(javaScriptExpression, this, 0).invoke()

/**
 * Executes a JavaScript expression in the JBCefBrowser and returns the result.
 *
 * @param javaScriptExpression The JavaScript expression to be executed.
 * The passed JavaScript code should be either:
 * * a valid single-line JavaScript expression
 * * a valid multi-line function-body with at least one "return" statement
 *
 * Examples:
 * ```Kotlin
 *
 *  launch {
 *    browser.executeJavaScript("2 + 2")
 *       .let { r -> r /* r is 4 */ }
 *
 *    browser.executeJavaScript("return 2 + 2")
 *       .let { r -> r /* r is 4 */ }
 *
 *    browser.executeJavaScript("""
 *          function sum(s1, s2) {
 *              return s1 + s2;
 *          };
 *
 *          return sum(2,2);
 *    """.trimIndent())
 *       .let { r -> r /* r is 4 */ }
 *  }
 * ```
 *
 * @return The result of the JavaScript expression execution.
 */
suspend fun JBCefBrowser.executeJavaScript(@Language("JavaScript") javaScriptExpression: JsExpression,
                                           frameId: Int = 0): JsExpressionResult =
  JBCefBrowserJsCall(javaScriptExpression, this, frameId).await()


open class JBCefBrowserJsCall(private val javaScriptExpression: JsExpression, private val browser: JBCefBrowser, private val frameId: Int) {
  // TODO: Ensure the related JBCefClient has a sufficient number of slots in the pool

  private val debugId = debugIdCounter.incrementAndGet()

  //region Promise API

  /**
   * Performs [javaScriptExpression] in the JCEF [browser] asynchronously.
   * @return The [Promise] that provides JS execution result or an error.
   * @throws IllegalStateException if the related [browser] is not initialized (displayed).
   * @throws IllegalStateException if the related [browser] is disposed.
   * @see [com.intellij.ui.jcef.JBCefBrowserBase.isCefBrowserCreated]
   */
  @Deprecated("Use await() and coroutines instead", ReplaceWith("await()"))
  operator fun invoke(): Promise<JsExpressionResult> {
    if (browser.isCefBrowserCreated.not())
      throw IllegalStateException("Failed to execute the requested JS expression. The related JCEF browser in not initialized.")

    /**
     * The root [com.intellij.openapi.Disposable] object that indicates the lifetime of this call.
     * Remains undisposed until the [javaScriptExpression] gets executed in the [browser] (either successfully or with an error).
     */
    val executionLifetime: CheckedDisposable = Disposer.newCheckedDisposable().also { Disposer.register(browser, it) }

    if (executionLifetime.isDisposed)
      throw IllegalStateException(
        "Failed to execute the requested JS expression. The related browser is disposed.")

    val resultPromise = JsExpressionResultPromise().apply {
      onProcessed {
        Disposer.dispose(executionLifetime)
      }
    }

    Disposer.register(executionLifetime) {
      resultPromise.setError("The related browser is disposed during the call.")
    }

    val resultHandlerQuery: JBCefJSQuery = createResultHandlerQueryWithinScope(executionLifetime, resultPromise)
    val errorHandlerQuery: JBCefJSQuery = createErrorHandlerQueryWithinScope(executionLifetime, resultPromise)

    val jsToRun = javaScriptExpression.wrapWithErrorHandling(resultQuery = resultHandlerQuery, errorQuery = errorHandlerQuery)

    try {
      browser.cefBrowser.executeJavaScript(jsToRun, "", frameId)
    }
    catch (ex: Exception) {
      // In case something goes wrong with the browser interop
      resultPromise.setError(ex)
    }

    return resultPromise
  }

  private fun createResultHandlerQueryWithinScope(parentDisposable: Disposable, resultPromise: JsExpressionResultPromise) =
    createQueryWithinScope(parentDisposable).apply {
      addHandler { result ->
        resultPromise.setResult(result)
        null
      }
    }

  private fun createErrorHandlerQueryWithinScope(parentDisposable: Disposable, resultPromise: JsExpressionResultPromise) =
    createQueryWithinScope(parentDisposable).apply {
      addHandler { errorMessage ->
        resultPromise.setError(errorMessage ?: "Unknown error")
        null
      }
    }

  private fun createQueryWithinScope(parentDisposable: Disposable): JBCefJSQuery = create(browser as JBCefBrowserBase).also {
    Disposer.register(parentDisposable, it)
  }

  //endregion

  //region Coroutines API

  suspend fun await(): JsExpressionResult = coroutineScope {
    // `coroutineScope` ensures all child jobs will be finished before exiting the scope
    if (browser.isCefBrowserCreated.not())
      throw IllegalStateException("Failed to execute the requested JS expression. The related JCEF browser in not initialized.")

    val deferredJsExecutionResult =
      async(start = CoroutineStart.LAZY) {
        debugIfEnabled("Starting: JCEF browser JS call coroutine")
        suspendCancellableCoroutine { continuation ->
          javaScriptExpression
            .wrapWithErrorHandling(resultQuery = createResultHandlerQueryWithinScope(continuation),
                                   errorQuery = createErrorHandlerQueryWithinScope(continuation))
            .also { jsToRun ->
              debugIfEnabled("Executing: JavaScript expression in JCEF browser")
              browser.cefBrowser.executeJavaScript(jsToRun, "", frameId)
              debugIfEnabled("Executed: JavaScript expression in JCEF browser")
            }
        }.also {
          debugIfEnabled("Stopping: JCEF browser JS call coroutine")
        }
      }

    val executionLifetime = Disposable {
      deferredJsExecutionResult.cancel()
      debugIfEnabled("Execution lifetime: Disposed")
    }

    debugIfEnabled("Execution lifetime: Initialized")

    // In case browser is disposed anytime before the job is completed, the related request will be canceled
    Disposer.register(browser, executionLifetime)

    try {
      deferredJsExecutionResult.await()
    }
    finally {
      debugIfEnabled("Execution lifetime: Disposing")
      Disposer.dispose(executionLifetime)
    }
  }

  private fun CoroutineScope.createResultHandlerQueryWithinScope(continuation: Continuation<JsExpressionResult>) =
    createQueryWithinScope().apply {
      addHandler { result ->
        debugIfEnabled("Resume with result")
        continuation.resume(result)
        null
      }
    }

  private fun CoroutineScope.createErrorHandlerQueryWithinScope(continuation: Continuation<JsExpressionResult>) =
    createQueryWithinScope().apply {
      addHandler { errorMessage ->
        debugIfEnabled("Resume with error")
        continuation.resumeWithException(JBCefBrowserJsCallError(errorMessage ?: "Unknown error"))
        null
      }
    }

  private fun CoroutineScope.createQueryWithinScope(): JBCefJSQuery = create(browser as JBCefBrowserBase).also { query ->
    debugIfEnabled("Created JCEF query handler: `${query.funcName}`")

    coroutineContext.job.invokeOnCompletion {
      debugIfEnabled("Disposing JCEF query handler: `${query.funcName}`")
      Disposer.dispose(query)
    }
  }

  // endregion

  private fun JsExpression.asFunctionBody(): JsExpression = let { expression ->
    when {
      StringUtil.containsLineBreak(expression) -> expression
      StringUtil.startsWith(expression, "return") -> expression
      else -> "return $expression"
    }
  }

  @Suppress("JSVoidFunctionReturnValueUsed")
  @Language("JavaScript")
  private fun @receiver:Language("JavaScript") JsExpression.wrapWithErrorHandling(resultQuery: JBCefJSQuery, errorQuery: JBCefJSQuery) = """
      function payload() {
          ${asFunctionBody()}
      }

      try {
          let result = payload();

          // call back the related JBCefJSQuery
          window.${resultQuery.funcName}({
              request: "" + result,
              onSuccess: function (response) {
                  // do nothing
              }, onFailure: function (error_code, error_message) {
                  // do nothing
              }
          });
      } catch (e) {
          // call back the related error handling JBCefJSQuery
          window.${errorQuery.funcName}({
              request: "" + e,
              onSuccess: function (response) {
                  // do nothing
              }, onFailure: function (error_code, error_message) {
                  // do nothing
              }
          });
      }
    """.trimIndent()

  private fun debugIfEnabled(message: String) {
    if (logger.isDebugEnabled) {
      logger.debug("[ $debugId ] $message | Thread: ${Thread.currentThread().name}")
    }
  }

  companion object {
    @JvmStatic
    private val debugIdCounter = AtomicInteger(0)

    @JvmStatic
    private val logger = logger<JBCefBrowserJsCall>()
  }
}
