// WITH_STDLIB
package org.apache.logging.log4j

class Foo {
    private val logger = LogManager.getLogger(<caret>Bar::class.java)
}

class Bar

object LogManager {
    fun getLogger(clazz: Class<*>) {}
}