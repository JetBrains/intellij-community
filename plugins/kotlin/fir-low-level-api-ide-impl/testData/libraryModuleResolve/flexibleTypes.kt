// WITH_LIBRARY: _library
// LOAD_AST: library.WithFlexibleTypes
package test

import library.WithFlexibleTypes

fun usage() {
    WithFlexibleTypes().s<caret>tr
}