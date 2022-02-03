// "Make 'prop' private" "true"
open class Base {
    fun baseFun(param: Any) {}
}

open class Derived : Base() {
    inline val prop: Unit
        get() {
            <caret>super.baseFun("123")
        }
}