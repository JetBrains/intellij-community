package pack

import pack.BaseClass.Companion.objectFunction

open class BaseClass {
    companion object {
        fun objectFunction() {}
    }
}

class Unrelated {
    /**
     * [objectFunction]
     */
    fun usage() {

    }
}
