// WITH_LIBRARY: _library
package test

import library.topLevelFunction

fun usage() {
    <caret>topLevelFunction("")
}