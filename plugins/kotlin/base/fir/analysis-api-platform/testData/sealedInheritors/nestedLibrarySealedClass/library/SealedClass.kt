class RegularClass {
    sealed class NestedSealedClass {
        class Nested2SealedClassInheritor1 : NestedSealedClass() {
            class Nested3SealedClassInheritor1 : NestedSealedClass()
            object Nested3SealedClassInheritor2 : NestedSealedClass()
        }
    }

    class NestedSealedClassInheritor1 : NestedSealedClass()
    object NestedSealedClassInheritor2 : NestedSealedClass()
}
