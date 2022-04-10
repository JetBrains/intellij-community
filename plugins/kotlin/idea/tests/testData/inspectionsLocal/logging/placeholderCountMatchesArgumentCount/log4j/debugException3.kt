// PROBLEM: Fewer arguments provided (0) than placeholders specified (1)
// FIX: none
package org.apache.logging.log4j

private val logger: Logger? = null

fun foo(a: Int, b: Int) {
    logger?.debug("<caret>test {}", Exception())
}

interface Logger {
    fun debug(format: String, param1: Any)
}