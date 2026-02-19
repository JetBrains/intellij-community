// IS_APPLICABLE: false
package test.pack

class This {
    val a = <caret>this.invoke()
    operator fun invoke() = Unit
}
