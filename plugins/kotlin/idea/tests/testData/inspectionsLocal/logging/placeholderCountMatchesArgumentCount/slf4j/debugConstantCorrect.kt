// PROBLEM: none
package org.slf4j

private val logger: Logger? = null

private val brackets: String = "{}"

fun foo(a: Int, b: Int) {
    logger?.debug("<caret>test {}" + brackets, 1, 2)
}

interface Logger {
    fun trace(format: String, vararg params: Any)
    fun debug(format: String, vararg params: Any)
    fun info(format: String, vararg params: Any)
    fun warn(format: String, vararg params: Any)
    fun error(format: String, vararg params: Any)
}