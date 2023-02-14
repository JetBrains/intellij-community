// PROBLEM: More arguments provided (3) than placeholders specified (2)
// FIX: none
package org.slf4j

private val logger: Logger? = null

fun foo(a: Int, b: Int) {
    logger?.debug("<caret>test {} {}", *arrayOf(1, 2, 3))
}

interface Logger {
    fun trace(format: String, vararg params: Any)
    fun debug(format: String, vararg params: Any)
    fun info(format: String, vararg params: Any)
    fun warn(format: String, vararg params: Any)
    fun error(format: String, vararg params: Any)
}