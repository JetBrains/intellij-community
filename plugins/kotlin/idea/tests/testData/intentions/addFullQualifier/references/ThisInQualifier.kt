// IS_APPLICABLE: false
package test.pack

open class A(var a: String) {
    fun dummySetter(a: String) {
        <caret>this.a = a
    }
}
