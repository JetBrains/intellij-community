// "Make 'inlineFun' internal" "true"
open class Base {
    fun baseFun(param: Any) {}
}

open class Derived : Base() {
    inline fun inlineFun() {
        <caret>super.baseFun("123")
    }
}