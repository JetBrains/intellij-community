// IS_APPLICABLE: false
package test.pack

class This {
    val a = <caret>this()
    operator fun invoke() = Unit
}
