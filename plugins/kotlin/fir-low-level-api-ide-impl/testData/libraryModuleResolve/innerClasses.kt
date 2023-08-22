// WITH_LIBRARY: _library
// LOAD_AST: library.WithInner
package test

import library.WithInner

fun usage() {
    WithInner().f<caret>oo()
}