package library
interface Foo {
    companion object {
        operator fun invoke() = object : Foo {}
    }
}

