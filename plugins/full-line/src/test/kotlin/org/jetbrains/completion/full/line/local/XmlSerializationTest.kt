package org.jetbrains.completion.full.line.local

import nl.adaptivity.xmlutil.serialization.XML
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals

abstract class XmlSerializationTest {
    protected val xml = XML {
        policy = CustomJacksonPolicy()
        // For better comparison / debugging
        indent = 1
        // Skipping unknown without any exception
        unknownChildHandler = { _, _, _, _ -> }
    }

    /**
     * This function is only needed to enable highlighting for xml in a parameter.
     */
    protected fun xml(@Language("XML") data: String) = data

    protected  fun assertEqualsWithoutIndent(expected: Any, actual: Any) = assertEquals(
        expected.toString().removeIndent(),
        actual.toString().removeIndent(),
    )

    private fun String.removeIndent() = lineSequence().joinToString("\n") {
        it.trimStart()
    }
}