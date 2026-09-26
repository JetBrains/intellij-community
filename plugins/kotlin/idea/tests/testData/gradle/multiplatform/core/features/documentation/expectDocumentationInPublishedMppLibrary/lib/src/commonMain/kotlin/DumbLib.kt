//region Test configuration
// - hidden: line markers
//endregion
package com.example.dumblib

/**
 * Common documentation for DumbLib
 */
expect object DumbLib {

    /**
     * Common doc for foo
     *
     * @param arg1 common doc for arg1
     *
     */
    fun foo(
        arg1: Int,
        /**
         * Common doc for arg2
         */
        arg2: Int,
    ): Int
}
