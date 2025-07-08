// IGNORE_K1
package foo

fun test() = when (42) {
    /**
     * [Ba<caret>]
     */
    is Any -> {}
}

// ELEMENT: Bar
