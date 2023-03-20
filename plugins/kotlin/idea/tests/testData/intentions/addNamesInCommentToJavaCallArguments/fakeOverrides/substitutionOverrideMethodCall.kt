class KGeneric : Generic<Int>()

fun test() {
    KGeneric().foo<caret>(10)
}