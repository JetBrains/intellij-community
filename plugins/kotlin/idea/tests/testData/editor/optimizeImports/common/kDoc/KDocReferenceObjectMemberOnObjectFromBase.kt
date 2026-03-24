// IGNORE_K1
package pack

import pack.MyObject.objectFunction

open class BaseClass {
    fun objectFunction() {}
}

object MyObject : BaseClass() {
    /**
     * [objectFunction]
     */
    fun usage() {

    }
}
