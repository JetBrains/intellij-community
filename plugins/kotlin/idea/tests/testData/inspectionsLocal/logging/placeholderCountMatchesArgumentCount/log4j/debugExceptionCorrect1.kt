// PROBLEM: none
package org.apache.logging.log4j

private val logger: Logger? = null

fun foo(a: Int, b: Int) {
    logger?.debug("<caret>test {} {}", 1, 2, Exception())
}

interface Logger {
    fun debug(format: String, param1: Any, param2: Any, param3: Any)
}