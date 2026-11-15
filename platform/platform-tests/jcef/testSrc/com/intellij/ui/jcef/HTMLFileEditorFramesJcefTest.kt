// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.ide.browsers.actions.WebPreviewFileType
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider
import com.intellij.openapi.fileEditor.impl.HTMLVirtualFile
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.ui.scale.TestScaleHelper
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.awt.BorderLayout
import java.util.concurrent.ConcurrentLinkedQueue
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Tests that the `jbCefQuery` function of an HTML editor tab answers only the main frame of its page, in real JCEF.
 * The test runs only with `-Dintellij.test.standalone=true`, `-Djava.awt.headless=false` and native JCEF.
 */
class HTMLFileEditorFramesJcefTest {
  companion object {
    @ClassRule
    @JvmField
    val projectRule: ProjectRule = ProjectRule()
  }

  @Rule
  @JvmField
  val disposableRule: DisposableRule = DisposableRule()

  private lateinit var server: TestPageServer

  @Before
  fun before() {
    TestScaleHelper.assumeStandalone()
    TestScaleHelper.assumeHeadful()
    server = TestPageServer()
  }

  @After
  fun after() {
    if (::server.isInitialized) server.close()
  }

  @Test
  fun `the query of an HTML request answers its main frame and refuses its iframe`() {
    val requests = ConcurrentLinkedQueue<String>()
    val call = """
      window.${HTMLEditorProvider.JS_FUNCTION_NAME}({request: NAME,
        onSuccess: function(r) { report(NAME, 'query', 'ok'); },
        onFailure: function(code, message) { report(NAME, 'query', 'fail ' + code); }});
    """.trimIndent()
    server.serveSubframe("html", call)

    @Language("HTML")
    val html = """
      <!DOCTYPE html>
      <html>
      <body>
      <script>
        function report(frame, fn, result) {
          fetch('${server.mainOrigin}/report?' + encodeURIComponent(frame + ' ' + fn + ' ' + result), {mode: 'no-cors'});
        }
        const NAME = 'html-main';
        $call
      </script>
      <iframe src="${server.otherOrigin}/html-frame?name=html-frame"></iframe>
      </body>
      </html>
    """.trimIndent()
    val request = HTMLEditorProvider.Request.html(html).withQueryHandler(object : HTMLEditorProvider.JsQueryHandler {
      override suspend fun query(id: Long, request: String): String {
        requests.add(request)
        return "answer"
      }
    })

    openEditor(request)

    val results = server.awaitResults(setOf("html-main" to "query", "html-frame" to "query")).mapKeys { it.key.first }
    assertEquals(mapOf("html-main" to "ok", "html-frame" to "fail ${JBCefJSQuery.ERR_CODE_FRAME_REFUSED}"), results)
    assertEquals(listOf("html-main"), requests.toList())
  }

  @Test
  fun `the query of a URL request answers the main frame of a trusted origin`() {
    val requests = servePage("trusted")
    openEditor(urlRequest("trusted", requests).withTrustedOrigins(server.mainOrigin))

    val results = server.awaitResults(setOf("trusted-main" to "query", "trusted-frame" to "query")).mapKeys { it.key.first }
    assertEquals(mapOf("trusted-main" to "ok", "trusted-frame" to "fail ${JBCefJSQuery.ERR_CODE_FRAME_REFUSED}"), results)
    assertEquals(listOf("trusted-main"), requests.toList())
  }

  @Test
  fun `the query of a URL request refuses a main frame of an origin that it does not trust`() {
    val requests = servePage("other-origin")
    openEditor(urlRequest("other-origin", requests).withTrustedOrigins(server.otherOrigin))

    val results = server.awaitResults(setOf("other-origin-main" to "query")).mapKeys { it.key.first }
    assertEquals(mapOf("other-origin-main" to "fail ${JBCefJSQuery.ERR_CODE_FRAME_REFUSED}"), results)
    assertEquals(emptyList<String>(), requests.toList())
  }

  private fun call(): String = """
    window.${HTMLEditorProvider.JS_FUNCTION_NAME}({request: NAME,
      onSuccess: function(r) { report(NAME, 'query', 'ok'); },
      onFailure: function(code, message) { report(NAME, 'query', 'fail ' + code); }});
  """.trimIndent()

  // Serves a page of the main origin, with an iframe of the other origin, that calls the query from both frames.
  private fun servePage(tag: String): ConcurrentLinkedQueue<String> {
    server.serveSubframe(tag, call())
    @Language("HTML")
    val html = """
      <!DOCTYPE html>
      <html>
      <body>
      <script>
        function report(frame, fn, result) {
          fetch('/report?' + encodeURIComponent(frame + ' ' + fn + ' ' + result), {mode: 'no-cors'});
        }
        const NAME = '$tag-main';
        ${call()}
      </script>
      <iframe src="${server.otherOrigin}/$tag-frame?name=$tag-frame"></iframe>
      </body>
      </html>
    """.trimIndent()
    server.serve("/$tag", html)
    return ConcurrentLinkedQueue()
  }

  private fun urlRequest(tag: String, requests: ConcurrentLinkedQueue<String>): HTMLEditorProvider.Request {
    return HTMLEditorProvider.Request.url("${server.mainOrigin}/$tag").withQueryHandler(object : HTMLEditorProvider.JsQueryHandler {
      override suspend fun query(id: Long, request: String): String {
        requests.add(request)
        return "answer"
      }
    })
  }

  private fun openEditor(request: HTMLEditorProvider.Request) {
    val project = projectRule.project
    val file = HTMLVirtualFile.createFile(project, "frames", request, WebPreviewFileType.INSTANCE, ignoreJcef = false)
    val provider = FileEditorProviderManager.getInstance().getProviderList(project, file).single { it.editorTypeId == "html-editor" }
    SwingUtilities.invokeAndWait {
      val editor = provider.createEditor(project, file)
      Disposer.register(disposableRule.disposable, editor)
      val frame = JFrame(HTMLFileEditorFramesJcefTest::class.java.simpleName)
      frame.setSize(640, 480)
      frame.add(editor.component, BorderLayout.CENTER)
      frame.isVisible = true
      Disposer.register(disposableRule.disposable) { frame.dispose() }
    }
  }
}
