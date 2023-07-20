// WITH_LIBRARY: _library
package test

import library.TopLevelClass

fun usage() {
    TopLevelClass().<caret>memberFunction()
}
