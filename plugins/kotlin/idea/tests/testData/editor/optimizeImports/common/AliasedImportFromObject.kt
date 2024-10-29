package test

import test.MyObject.originalFunction
import test.MyObject.originalFunction as aliasedFunction

object MyObject {
    fun originalFunction() {}

    fun usage() {
        originalFunction()

        aliasedFunction()
    }
}