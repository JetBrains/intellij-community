// COMPILER_ARGUMENTS: -Xcompanion-blocks

class Foo(val v: Int) {
    fun b<caret>ar(param: Int): String {
        println(this.v + 1)
        return "$param $v"
    }

    fun baz() {
        bar(1)
    }

    companion {
    }
}

fun boo(f: Foo): String {
    f.bar(2)
    return f.bar(3)
}

