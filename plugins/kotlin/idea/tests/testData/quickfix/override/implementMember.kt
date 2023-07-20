// "Implement members" "true"
// WITH_STDLIB
annotation class Annotation
interface I {
    @Annotation
    fun foo()
}

<caret>class A : I
