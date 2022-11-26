abstract class GenericB<B> : GenericAB<Int, B>()
class Concrete : GenericB<String>()

fun test() {
    Concrete().foo<caret>(10, "hello")
}