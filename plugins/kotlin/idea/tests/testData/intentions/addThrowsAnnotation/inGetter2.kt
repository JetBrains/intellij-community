// WITH_STDLIB

@get:Throws(RuntimeException::class)
val a: String
    get() = <caret>throw Exception()