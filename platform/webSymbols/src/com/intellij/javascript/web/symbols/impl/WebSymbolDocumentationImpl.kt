package com.intellij.javascript.web.symbols.impl

import com.intellij.javascript.web.symbols.WebSymbolDocumentation
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal data class WebSymbolDocumentationImpl(override val name: String,
                                               override val definition: String,
                                               override val description: @Nls String?,
                                               override val docUrl: String?,
                                               override val deprecated: Boolean,
                                               override val experimental: Boolean,
                                               override val required: Boolean,
                                               override val defaultValue: String?,
                                               override val library: String?,
                                               override val icon: Icon?,
                                               override val descriptionSections: Map<@Nls String, @Nls String>,
                                               override val footnote: @Nls String?) : WebSymbolDocumentation