// WITH_LIBRARY: _library
// LOAD_AST: library.FunctionWithContract
package test

import library.FunctionWithContract

fun usage() {
    FunctionWithContract().re<caret>quire(true)
}