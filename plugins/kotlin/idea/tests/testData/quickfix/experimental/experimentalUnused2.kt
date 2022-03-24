// "Safe delete 'Marker'" "false"
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn -opt-in=test.Marker
// WITH_STDLIB
// ACTION: Rename file to Marker.kt
// TOOL: org.jetbrains.kotlin.idea.inspections.UnusedSymbolInspection

package test

@RequiresOptIn
annotation class <caret>Marker
