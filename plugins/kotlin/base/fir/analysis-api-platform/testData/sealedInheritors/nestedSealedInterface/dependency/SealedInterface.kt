class RegularClass {
    sealed interface NestedSealedInterface {
        class Nested2SealedInterfaceInheritor1 : NestedSealedInterface {
            interface Nested3SealedInterfaceInheritor1 : NestedSealedInterface
            object Nested3SealedInterfaceInheritor2 : NestedSealedInterface
        }
    }

    class NestedSealedInterfaceInheritor1 : NestedSealedInterface
    object NestedSealedInterfaceInheritor2 : NestedSealedInterface
}
