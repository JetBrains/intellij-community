package library
class Foo(i: Int) {
    companion object {
        operator fun invoke() = 1
    }
}

fun f1() {
    Foo()
}

