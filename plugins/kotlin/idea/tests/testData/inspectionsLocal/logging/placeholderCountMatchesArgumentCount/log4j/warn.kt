// PROBLEM: Fewer arguments provided (2) than placeholders specified (3)
// FIX: none
package org.apache.logging.log4j

private val logger: Logger? = null

fun foo(a: Int, b: Int) {
    logger?.warn("<caret>test {} {} {}", 1, 2)
}

interface Logger {
    fun warn(format: String, param1: Any, param2: Any)
}