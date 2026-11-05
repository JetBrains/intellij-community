// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.text.StringUtil
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.network.CefRequest
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private val LOG = logger<JBCefClipboardBridge>()

private const val COPY_PREFIX = "copy:"
private const val ERROR_PREFIX = "error:"
private const val PASTE_REQUEST = "paste"

/**
 * Routes the clipboard traffic of an off-screen browser through the IDE process.
 *
 * - Copy and cut send text and HTML to the IDE through a [JBCefJSQuery].
 * - Paste sends text and HTML to Chromium before it runs a regular paste.
 *
 * For pages without a secure context, paste uses a synthetic event. The browser builder selects the default based on
 * the Ozone platform and desktop environment. The system property `ide.browser.jcef.linux.clipboard.bridge` can override it.
 */
@ApiStatus.Internal
class JBCefClipboardBridge internal constructor(private val browser: JBCefBrowserBase) {
  private val query: JBCefJSQuery = JBCefJSQuery.create(browser)
  private val copyScript: String = buildCopyScript()

  init {
    query.addHandler(::handleQuery)
    browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
      override fun onLoadStart(cefBrowser: CefBrowser, frame: CefFrame, transitionType: CefRequest.TransitionType) {
        installCopyScript(frame)
      }

      override fun onLoadEnd(cefBrowser: CefBrowser, frame: CefFrame?, httpStatusCode: Int) {
        installCopyScript(frame)
      }
    }, browser.cefBrowser)
  }

  /**
   * Pastes the system clipboard content, as the IDE sees it, into the focused frame of the browser.
   * Without text or HTML in the clipboard, Chromium pastes from its own clipboard.
   */
  fun paste() {
    val frame = browser.cefBrowser.focusedFrame ?: return
    val clipboard = CopyPasteManager.getInstance()
    val text = clipboard.getContents<String>(DataFlavor.stringFlavor).orEmpty()
    val html = clipboard.getContents<String>(DataFlavor.allHtmlFlavor).orEmpty()
    if (text.isEmpty() && html.isEmpty()) {
      frame.paste()
    }
    else {
      frame.executeJavaScript(buildPasteScript(text, html), frame.url, 0)
    }
  }

  private fun installCopyScript(frame: CefFrame?) {
    if (frame != null && frame.isValid) frame.executeJavaScript(copyScript, frame.url, 0)
  }

  /** Copies the current frame selection through the script bridge. */
  fun copy(frame: CefFrame) {
    if (!frame.isValid) return
    installCopyScript(frame)
    frame.copy()
  }

  /** Cuts the current frame selection through the script bridge. */
  fun cut(frame: CefFrame) {
    if (!frame.isValid) return
    installCopyScript(frame)
    frame.cut()
  }

  private fun handleQuery(request: String): JBCefJSQuery.Response? {
    try {
      when {
        request.startsWith(COPY_PREFIX) -> onCopied(request.removePrefix(COPY_PREFIX))
        request.startsWith(ERROR_PREFIX) -> {
          val message = URLDecoder.decode(request.removePrefix(ERROR_PREFIX), StandardCharsets.UTF_8)
          LOG.warn("JCEF clipboard bridge: clipboard write failed: ${message.take(512)}")
        }
        // The page wrote the data into the clipboard of Chromium. A regular paste command reads it back.
        request == PASTE_REQUEST -> browser.cefBrowser.focusedFrame?.paste()
      }
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      LOG.warn("JCEF clipboard bridge: failed to handle a request", e)
    }
    return null
  }

  // The payload is "<text>\n<html>", both parts encoded with encodeURIComponent.
  private fun onCopied(payload: String) {
    val separator = payload.indexOf('\n')
    if (separator < 0) return
    val text = URLDecoder.decode(payload.substring(0, separator), StandardCharsets.UTF_8)
    val html = URLDecoder.decode(payload.substring(separator + 1), StandardCharsets.UTF_8)
    if (text.isEmpty() && html.isEmpty()) return

    ApplicationManager.getApplication().invokeLater({
      CopyPasteManager.getInstance().setContents(TextAndHtmlTransferable(text, html))
    }, ModalityState.any())
  }

  @Language("JavaScript")
  private fun buildCopyScript(): String {
    val send = query.inject("'$COPY_PREFIX' + encodeURIComponent(text) + '\\n' + encodeURIComponent(html)")
    return """
      (function() {
        if (window.__jbCefClipboardBridge) return;
        const bridge = window.__jbCefClipboardBridge = {};

        function send(text, html) {
          text = text == null ? '' : String(text);
          html = html == null ? '' : String(html);
          if (text === '' && html === '') return;
          $send
        }

        // A page that handles the copy itself puts its data here from a 'copy' or 'cut' listener.
        let captured = null;
        const setData = DataTransfer.prototype.setData;
        DataTransfer.prototype.setData = function(type, data) {
          if (captured) captured[String(type).toLowerCase()] = String(data);
          return setData.apply(this, arguments);
        };

        function selectionText() {
          const el = document.activeElement;
          if (el && (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') && typeof el.selectionStart === 'number') {
            return el.value.substring(el.selectionStart, el.selectionEnd);
          }
          const sel = document.getSelection();
          return sel ? sel.toString() : '';
        }

        function selectionHtml() {
          const sel = document.getSelection();
          if (!sel || sel.isCollapsed) return '';
          const div = document.createElement('div');
          for (let i = 0; i < sel.rangeCount; i++) div.appendChild(sel.getRangeAt(i).cloneContents());
          return div.innerHTML;
        }

        function onCopyOrCut() {
          // Take the selection now: 'cut' removes it before the event dispatch finishes.
          const text = selectionText(), html = selectionHtml();
          captured = {};
          setTimeout(function() {
            const data = captured;
            captured = null;
            if ('text/plain' in data || 'text/html' in data) send(data['text/plain'], data['text/html']);
            else send(text, html);
          }, 0);
        }
        document.addEventListener('copy', onCopyOrCut, true);
        document.addEventListener('cut', onCopyOrCut, true);

        // JS-initiated copies, e.g. a "copy code" button. The paste script uses the original write().
        const clipboard = navigator.clipboard;
        if (clipboard) {
          const writeText = clipboard.writeText.bind(clipboard);
          bridge.write = clipboard.write.bind(clipboard);
          clipboard.writeText = function(text) {
            send(text, '');
            return writeText(text);
          };
          clipboard.write = function(items) {
            (async function() {
              let text = '', html = '';
              for (const item of items) {
                if (item.types.includes('text/plain')) text = await (await item.getType('text/plain')).text();
                if (item.types.includes('text/html')) html = await (await item.getType('text/html')).text();
              }
              send(text, html);
            })().catch(function() {});
            return bridge.write(items);
          };
        }
      })();
      """.trimIndent()
  }

  @Language("JavaScript")
  private fun buildPasteScript(text: String, html: String): String {
    val notifyWritten = query.inject("'$PASTE_REQUEST'")
    val notifyWriteFailed = query.inject("'$ERROR_PREFIX' + encodeURIComponent(String(error))")
    return """
      (function() {
        const text = ${jsString(text)};
        const html = ${jsString(html)};

        // The page gets a synthetic paste event. When the page does not handle it, the text goes in through insertText.
        function pasteSynthetic() {
          const data = new DataTransfer();
          data.setData('text/plain', text);
          if (html) data.setData('text/html', html);
          const target = document.activeElement || document.body;
          if (target.dispatchEvent(new ClipboardEvent('paste', {clipboardData: data, bubbles: true, cancelable: true}))) {
            document.execCommand('insertText', false, text);
          }
        }

        const bridge = window.__jbCefClipboardBridge;
        const write = bridge ? bridge.write : navigator.clipboard && navigator.clipboard.write.bind(navigator.clipboard);
        if (!write || !document.hasFocus()) {
          pasteSynthetic();
          return;
        }
        const items = {'text/plain': new Blob([text], {type: 'text/plain'})};
        if (html) items['text/html'] = new Blob([html], {type: 'text/html'});
        // The data is in the clipboard of Chromium now. The IDE runs a regular paste, so the page gets a real paste event.
        write([new ClipboardItem(items)]).then(function() { $notifyWritten }, function(error) {
          $notifyWriteFailed;
          pasteSynthetic();
        });
      })();
      """.trimIndent()
  }
}

// A double-quoted JS string literal.
private fun jsString(s: String): String = "\"" + StringUtil.escapeStringCharacters(s) + "\""

private class TextAndHtmlTransferable(private val text: String, private val html: String) : Transferable {
  private val flavors: Array<DataFlavor> =
    if (html.isEmpty()) arrayOf(DataFlavor.stringFlavor)
    else arrayOf(DataFlavor.allHtmlFlavor, DataFlavor.stringFlavor)

  override fun getTransferDataFlavors(): Array<DataFlavor> = flavors.clone()

  override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor in flavors

  override fun getTransferData(flavor: DataFlavor): Any = when (flavor) {
    DataFlavor.stringFlavor -> text
    in flavors -> html
    else -> throw UnsupportedFlavorException(flavor)
  }
}
