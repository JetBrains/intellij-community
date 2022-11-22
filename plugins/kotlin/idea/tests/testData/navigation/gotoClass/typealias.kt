typealias TestGlobal = Any

fun some() {
    typealias TestInFun = Any
}

interface SomeInterface {
    typealias TestInInterface = Any
}

class Some() {
    typealias TestInClass = Any

    companion object {
        typealias TestInClassObject = Any
    }
}

// SEARCH_TEXT: Test
// REF: (<root>).TestGlobal
// REF: (in Some).TestInClass
// REF: (in Some.Companion).TestInClassObject
// REF: (in SomeInterface).TestInInterface