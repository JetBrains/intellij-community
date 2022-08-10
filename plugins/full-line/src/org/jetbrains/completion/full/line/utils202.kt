@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package org.jetbrains.completion.full.line

import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.layout.CellBuilder
import com.intellij.ui.layout.ComponentPredicate
import javax.swing.JComponent

// ====================================================================================
// File contains utils, that are not implemented in 202.x IDEA
// ====================================================================================

// com/intellij/openapi/diagnostic/logger.kt$thisLogger
inline fun <reified T : Any> T.thisLogger() = Logger.getInstance(T::class.java)

// com/intellij/ui/layout/migLayout/MigLayoutRow.kt$MigLayoutRow$visibleIf
fun <T : JComponent> CellBuilder<T>.visibleIf(predicate: ComponentPredicate): CellBuilder<T> {
    component.isVisible = predicate()
    predicate.addListener { component.isVisible = it }
    return this
}
