// WITH_STDLIB

class A {
    var a<caret>: List<String>

    init {
        a = emptyList()
    }
}
