// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.kdoc

import com.intellij.lang.documentation.DocumentationMarkup.CLASS_BOTTOM
import com.intellij.lang.documentation.DocumentationMarkup.CONTENT_END
import com.intellij.lang.documentation.DocumentationMarkup.CONTENT_START
import com.intellij.lang.documentation.DocumentationMarkup.DEFINITION_END
import com.intellij.lang.documentation.DocumentationMarkup.DEFINITION_START
import com.intellij.lang.documentation.DocumentationMarkup.SECTIONS_END
import com.intellij.lang.documentation.DocumentationMarkup.SECTIONS_START

open class KDocTemplate : Template<StringBuilder> {
    val definition = Placeholder<StringBuilder>()

    val description = Placeholder<StringBuilder>()

    val deprecation = Placeholder<StringBuilder>()

    val containerInfo = Placeholder<StringBuilder>()

    override fun StringBuilder.apply() {
        append(DEFINITION_START)
        insert(definition)
        append(DEFINITION_END)

        if (!deprecation.isEmpty()) {
            append(SECTIONS_START)
            insert(deprecation)
            append(SECTIONS_END)
        }

        insert(description)

        if (!containerInfo.isEmpty()) {
            append("<div class='$CLASS_BOTTOM'>")
            insert(containerInfo)
            append("</div>")
        }
    }

    sealed class DescriptionBodyTemplate : Template<StringBuilder> {
        class Kotlin : DescriptionBodyTemplate() {
            val content = Placeholder<StringBuilder>()
            val sections = Placeholder<StringBuilder>()
            override fun StringBuilder.apply() {
                val computedContent = buildString { insert(content) }
                if (computedContent.isNotBlank()) {
                    append(CONTENT_START)
                    append(computedContent)
                    append(CONTENT_END)
                }

                append(SECTIONS_START)
                insert(sections)
                append(SECTIONS_END)
            }
        }

        class FromJava : DescriptionBodyTemplate() {
            override fun StringBuilder.apply() {
                append(body)
            }

            lateinit var body: String
        }
    }

    class NoDocTemplate : KDocTemplate() {

        val error = Placeholder<StringBuilder>()

        override fun StringBuilder.apply() {
            insert(error)
        }
    }
}

