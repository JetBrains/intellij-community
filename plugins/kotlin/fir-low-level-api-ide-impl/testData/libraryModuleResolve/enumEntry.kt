// WITH_LIBRARY: _library
package test

import library.TopLevelEnum

fun usage() {
    val a = TopLevelEnum.<caret>ENTRY1
}
