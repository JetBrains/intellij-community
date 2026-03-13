// IGNORE_K1
package pack

import pack.BaseClass.Companion.objectFunction

open class BaseClass {
    companion object {
        fun objectFunction() {}
    }
}

class MyClass : BaseClass() {
    /**
     * [objectFunction]
     */
    fun usage() {

    }
}
