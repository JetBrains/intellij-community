fun foo() {
    class LocalClass {}

    interface LocalInterface {}

    interface LocalInterfaceWithImpl {
        fun foo() {}
    }

    object LocalObject() {}
}

// SEARCH_TEXT: Local
