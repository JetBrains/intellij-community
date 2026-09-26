// WITH_STDLIB
// FIX: Replace with '::class.java' call
package test

class Generic<T>

fun usage() {
    Generic<Any>::javaClass<caret>
}