// PROBLEM: none
package org.apache.logging.log4j

private val logger: Logger? = null

fun foo(a: Int, b: Int) {
    logger?.atDebug()?.log("<caret>test {}", Exception())
}

interface LogBuilder:{
    fun log(format: String, param2: Any)
}
interface Logger {
    fun atDebug(): LogBuilder
}