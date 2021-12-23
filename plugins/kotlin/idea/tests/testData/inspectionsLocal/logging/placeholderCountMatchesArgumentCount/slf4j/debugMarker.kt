// PROBLEM: Fewer arguments provided (1) than placeholders specified (2)
// FIX: none
package org.slf4j

private val logger: Logger? = null

fun foo(a: Int, b: Int) {
    logger?.debug(object: Marker {}, "<caret>test {} {}", 1)
}

interface Logger {
    fun debug(marker: Marker, format: String, arg: Any)
}

interface Marker{

}