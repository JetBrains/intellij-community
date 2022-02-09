// PROBLEM: none
package org.apache.logging.log4j

private val logger: Logger? = null

private val brackets: String = "{}"

fun foo(a: Int, b: Int) {
    logger?.debug("<caret>test {}" + brackets, 1, 2)
}

interface Logger {
    fun debug(format: String, param1: Any, param2: Any)
}