// PROBLEM: none
package org.apache.logging.log4j

private val logger: Logger? = null

fun foo(a: Int, b: Int) {
    logger?.atDebug()?.log("<caret>test {} {}", 1, 2, Exception())
}

interface LogBuilder:{
    fun log(format: String, param1: Any, param2: Any, param3: Any)
}
interface Logger {
    fun atDebug(): LogBuilder
}