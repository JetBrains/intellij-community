// WITH_LIBRARY: _library
// LOAD_AST: library.WithAnno
package test

import library.WithAnno

fun usage() {
    val w: With<caret>Anno? = null
}