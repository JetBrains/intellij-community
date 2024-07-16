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
// REF: (in Some).testInClass
// REF: (in Some.Companion).testInClassObject
// REF: (in SomeInterface).testInInterface
// REF: testGlobal