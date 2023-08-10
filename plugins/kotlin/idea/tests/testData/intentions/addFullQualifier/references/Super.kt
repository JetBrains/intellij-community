// IS_APPLICABLE: false
package check.replacement

open class A(var a: String)

class B : A("dummy") {
    val b = <caret>super.a
}
