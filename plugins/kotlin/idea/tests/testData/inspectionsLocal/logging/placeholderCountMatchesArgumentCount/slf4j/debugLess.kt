// PROBLEM: More arguments provided (2) than placeholders specified (1)
// FIX: none
package org.slf4j

private val logger: Logger? = null

fun foo(a: Int, b: Int) {
    logger?.debug("<caret>test {}", 1, 2)
}

interface Logger {
    fun trace(format: String, vararg params: Any)
    fun debug(format: String, vararg params: Any)
    fun info(format: String, vararg params: Any)
    fun warn(format: String, vararg params: Any)
    fun error(format: String, vararg params: Any)
}