interface AB : A, B

fun test(ab: AB) {
    ab.foo<caret>(10)
}