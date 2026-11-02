// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.kdoc

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import org.jetbrains.kotlin.idea.highlighting.kdoc.KDocRenderer.renderKDoc
import org.jetbrains.kotlin.psi.KtFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.StringReader
import javax.swing.text.AttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLEditorKit

@TestApplication
class KDocImageRenderingTest {
    companion object {
        private val project: TestFixture<Project> = projectFixture()
        private val module: TestFixture<Module> = project.moduleFixture("images")
        private val sourceRoot: TestFixture<PsiDirectory> = module.sourceRootFixture()
        private val sourceFile: TestFixture<PsiFile> = sourceRoot.psiFileFixture(
            "Images.kt",
            """
                /** ![**Direct** image](doc-files/diagram.png "Diagram") */
                fun directImage() {}

                /**
                 * ![Reference image][diagram]
                 *
                 * [diagram]: doc-files/diagram.png "Diagram"
                 */
                fun referenceImage() {}

                /**
                 * ![diagram]
                 *
                 * [diagram]: doc-files/diagram.png
                 */
                fun shortReferenceImage() {}

                /** <img src="doc-files/diagram.png" alt="HTML image" width="640" height="560"> */
                fun htmlImage() {}

                /** ![External image](https://example.com/diagram.png) */
                fun externalImage() {}

                /** ![A & "B"](doc-files/diagram.png?x=1&y=2) */
                fun escapedImage() {}

                /** `![Code](doc-files/diagram.png)` */
                fun codeImage() {}
            """.trimIndent()
        )
    }

    @Test
    fun `inline Markdown images render`(): Unit = timeoutRunBlocking {
        val image = imageAttributes("directImage")
        assertEquals(imageUrl(), image.getAttribute(HTML.Attribute.SRC))
        assertEquals("Direct image", image.getAttribute(HTML.Attribute.ALT))
        assertEquals("Diagram", image.getAttribute(HTML.Attribute.TITLE))
    }

    @Test
    fun `reference Markdown images render`(): Unit = timeoutRunBlocking {
        val image = imageAttributes("referenceImage")
        assertEquals(imageUrl(), image.getAttribute(HTML.Attribute.SRC))
        assertEquals("Reference image", image.getAttribute(HTML.Attribute.ALT))
        assertEquals("Diagram", image.getAttribute(HTML.Attribute.TITLE))
    }

    @Test
    fun `short reference Markdown images render`(): Unit = timeoutRunBlocking {
        val image = imageAttributes("shortReferenceImage")
        assertEquals(imageUrl(), image.getAttribute(HTML.Attribute.SRC))
        assertEquals("diagram", image.getAttribute(HTML.Attribute.ALT))
    }

    @Test
    fun `HTML images keep their size`(): Unit = timeoutRunBlocking {
        val image = imageAttributes("htmlImage")
        assertEquals(imageUrl(), image.getAttribute(HTML.Attribute.SRC))
        assertEquals("HTML image", image.getAttribute(HTML.Attribute.ALT))
        assertEquals("640", image.getAttribute(HTML.Attribute.WIDTH))
        assertEquals("560", image.getAttribute(HTML.Attribute.HEIGHT))
    }

    @Test
    fun `absolute image URLs stay absolute`(): Unit = timeoutRunBlocking {
        assertEquals("https://example.com/diagram.png", imageAttributes("externalImage").getAttribute(HTML.Attribute.SRC))
    }

    @Test
    fun `image text and query parameters keep their characters`(): Unit = timeoutRunBlocking {
        val image = imageAttributes("escapedImage")
        assertEquals(imageUrl() + "?x=1&y=2", image.getAttribute(HTML.Attribute.SRC))
        assertEquals("A & \"B\"", image.getAttribute(HTML.Attribute.ALT))
    }

    @Test
    fun `image syntax in code stays text`(): Unit = timeoutRunBlocking {
        val html = render("codeImage")
        assertFalse(html.contains("<img"), html)
    }

    private suspend fun render(name: String): String = readAction {
        val file = sourceFile.get() as KtFile
        val comment = file.declarations.single { it.name == name }.docComment!!
        buildString { renderKDoc(comment.getDefaultSection(), comment.getAllSections()) }
    }

    private suspend fun imageUrl(): String = readAction {
        VfsUtilCore.convertToURL(sourceFile.get().virtualFile.url)!!.toExternalForm()
            .replace("Images.kt", "doc-files/diagram.png")
    }

    private suspend fun imageAttributes(name: String): AttributeSet = parseImage(render(name))

    private fun parseImage(html: String): AttributeSet {
        val kit = HTMLEditorKit()
        val document = kit.createDefaultDocument() as HTMLDocument
        kit.read(StringReader(html), document, 0)
        val images = document.getIterator(HTML.Tag.IMG)
        assertTrue(images.isValid, html)
        return images.attributes
    }
}
