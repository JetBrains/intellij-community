interface AB : A, B
interface ABC : AB, C

fun test(abc: ABC) {
    abc.foo<caret>(10)
}