typealias testGlobal = Any

fun some() {
    typealias testInFun = Any
}

interface SomeInterface {
    typealias testInInterface = Any
}

class Some() {
    typealias testInClass = Any

    companion object {
        typealias testInClassObject = Any
    }
}

// SEARCH_TEXT: test