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
// REF: (in test.InClassObject.Companion).ClassObjectClass
// REF: (in test.InClassObject.Companion).ClassObjectInterface
// REF: (in test.InClassObject.Companion).ClassObjectInterfaceWithImpl
// REF: (in test.InClassObject.Companion).ClassObjectObject
