//region Test configuration
// - hidden: line markers
//endregion
package com.example.dumblib

actual object DumbLib {

    /**
     * JVM doc for foo
     *
     * @param arg1 JVM doc for arg1
     * @param arg2 JVM doc for arg2
     */
    actual fun foo(arg1: Int, arg2: Int): Int = 0
}
