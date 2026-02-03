class Foo {
    companion object {
        operator fun invoke(longOptionName: Int) = Foo()
    }
}


fun main(args: Array<String>) {
    Foo(longOptionNam<caret>)
}

// EXIST: {"lookupString":"longOptionName =","tailText":" Int","itemText":"longOptionName ="}
// IGNORE_K2