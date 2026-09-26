// WITH_STDLIB
// PROBLEM: none
interface Rec<R, out T: Rec<R, T>> {
    fun t(): T
}
interface Super {
    fun foo<caret>(p: Rec<*, *>) = p.t()
}