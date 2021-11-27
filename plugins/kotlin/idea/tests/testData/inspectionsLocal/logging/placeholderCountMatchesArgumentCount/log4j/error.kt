// FIX: none
package org.apache.logging.log4j

private val logger: Logger? = null

fun foo(a: Int, b: Int) {
    logger?.error("<caret>test {} {} {}", 1, 2)
}

interface Logger {
    fun error(format: String, param1: Any, param2: Any)
}