// PROBLEM: Fewer arguments provided (1) than placeholders specified (3)
// FIX: none
package org.apache.logging.log4j

private val logger: Logger? = null

fun foo(a: Int, b: Int) {
    logger?.atDebug()?.log("<caret>test {} {} {}", 1, Exception())
}

interface LogBuilder:{
    fun log(format: String, param1: Any, param2: Any)
}
interface Logger {
    fun atDebug(): LogBuilder
}