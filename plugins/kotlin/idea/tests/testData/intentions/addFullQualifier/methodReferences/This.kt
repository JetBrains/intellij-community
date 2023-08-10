// IS_APPLICABLE: false
package test.pack

class This {
    fun check() = Unit
    val a = <caret>this::check
}
