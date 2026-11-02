// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.kdoc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URI

class KDocHtmlSanitizerTest {
    @Test
    fun `accepted tags do not change`() {
        val html = "<b>bold</b>, <code>code</code> and <a href=\"https://example.com\">link</a><br/>"
        assertEquals(html, KDocHtmlSanitizer.sanitize(html))
    }

    @Test
    fun `image tags keep their attributes`() {
        val html = "<img src=\"https://example.com/diagram.png\" alt=\"Diagram\" width=\"640\" height=\"560\">"
        assertEquals(html, KDocHtmlSanitizer.sanitize(html))
    }

    @Test
    fun `relative image sources resolve against the source file`() {
        val baseUrl = URI("file:/project/p/C.kt").toURL()
        for (source in listOf("\"doc-files/diagram.png\"", "'doc-files/diagram.png'", "doc-files/diagram.png")) {
            assertEquals(
                "<img title=\"src='other.png'\" SRC=\"file:/project/p/doc-files/diagram.png\" width='640'>",
                KDocHtmlSanitizer.sanitize("<img title=\"src='other.png'\" SRC=$source width='640'>", baseUrl)
            )
        }
    }

    @Test
    fun `image sources resolve within source archives`() {
        assertEquals(
            "<img src=\"jar:file:/project/sources.jar!/doc-files/diagram.png?x=1&amp;y=2#part\">",
            KDocHtmlSanitizer.sanitize(
                "<img src='../doc-files/diagram.png?x=1&amp;y=2#part'>",
                URI("jar:file:/project/sources.jar!/p/C.kt").toURL()
            )
        )
    }

    @Test
    fun `empty and unsupported image sources stay unchanged`() {
        for (source in listOf("", "data:image/png;base64,abc")) {
            val html = "<img src=\"$source\">"
            assertEquals(html, KDocHtmlSanitizer.sanitize(html, URI("file:/project/p/C.kt").toURL()))
        }
    }

    @Test
    fun `superscript and subscript tags do not change`() {
        val html = "x<sup>2</sup> and H<sub>2</sub>O"
        assertEquals(html, KDocHtmlSanitizer.sanitize(html))
    }

    @Test
    fun `unknown tags are escaped`() {
        assertEquals(
            "List&lt;String&gt; and &lt;script&gt;alert(1)&lt;/script&gt;",
            KDocHtmlSanitizer.sanitize("List<String> and <script>alert(1)</script>"),
        )
    }

    @Test
    fun `some tags are replaced`() {
        assertEquals(
            "<span>block</span> <i>emphasis</i> <b>strong</b>",
            KDocHtmlSanitizer.sanitize("<div>block</div> <em>emphasis</em> <strong>strong</strong>"),
        )
    }

    @Test
    fun `tag names are case insensitive`() {
        assertEquals("<B>bold</B> <span>block</span>", KDocHtmlSanitizer.sanitize("<B>bold</B> <DIV>block</DIV>"))
    }

    @Test
    fun `text between tags does not change except a less-than sign`() {
        assertEquals("<pre>\n  a &lt; b && c > d\n</pre>", KDocHtmlSanitizer.sanitize("<pre>\n  a < b && c > d\n</pre>"))
    }

    @Test
    fun `an unknown tag with a tag in a double-quoted attribute is escaped`() {
        assertEquals(
            "&lt;script title=&quot;&lt;b&gt;&quot;&gt;alert(1)&lt;/script&gt; text",
            KDocHtmlSanitizer.sanitize("<script title=\"<b>\">alert(1)</script> text"),
        )
    }

    @Test
    fun `an unknown tag with a tag in a single-quoted attribute is escaped`() {
        assertEquals(
            "&lt;script title=&#39;&lt;b&gt;&#39;&gt;alert(1)&lt;/script&gt;",
            KDocHtmlSanitizer.sanitize("<script title='<b>'>alert(1)</script>"),
        )
    }

    @Test
    fun `an accepted tag keeps a quoted attribute with tags`() {
        val html = "<a title=\"</a><script>\">link</a>"
        assertEquals(html, KDocHtmlSanitizer.sanitize(html))
    }

    @Test
    fun `a tag with an unclosed quote is escaped`() {
        assertEquals("&lt;b title=\"x>bold", KDocHtmlSanitizer.sanitize("<b title=\"x>bold"))
    }

    @Test
    fun `a quote in an unquoted attribute value makes the tag invalid`() {
        assertEquals("&lt;b title=don't>bold", KDocHtmlSanitizer.sanitize("<b title=don't>bold"))
    }

    @Test
    fun `a tag with many attributes does not overflow the stack`() {
        val html = "<pre>\n<b " + "a ".repeat(4000) + ">text</b>\n</pre>"
        assertEquals(html, KDocHtmlSanitizer.sanitize(html))
    }

    @Test
    fun `a comment is escaped`() {
        assertEquals("&lt;!-- <b>note</b> -->", KDocHtmlSanitizer.sanitize("<!-- <b>note</b> -->"))
    }
}
