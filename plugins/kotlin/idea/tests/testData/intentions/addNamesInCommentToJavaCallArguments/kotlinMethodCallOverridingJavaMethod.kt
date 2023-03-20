// IS_APPLICABLE: false
class KGeneric : Generic<Int>() {
    override fun foo(t: Int) {}
}

fun test() {
    KGeneric().foo<caret>(10)
}