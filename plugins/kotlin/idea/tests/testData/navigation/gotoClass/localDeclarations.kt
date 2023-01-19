fun foo() {
    class LocalClass {}

    interface LocalInterface {}

    interface LocalInterfaceWithImpl {
        fun foo() {}
    }

    object LocalObject() {}
}

// SEARCH_TEXT: Local
// REF: (in foo).LocalClass
// REF: (in foo).LocalInterface
// REF: (in foo).LocalInterfaceWithImpl
// REF: (in foo).LocalObject
