package test

class InClassObject {
    companion object {
        class ClassObjectClass {}

        interface ClassObjectInterface {}

        interface ClassObjectInterfaceWithImpl {
            fun foo() {}
        }

        object ClassObjectObject() {}
    }
}

// SEARCH_TEXT: ClassObject
