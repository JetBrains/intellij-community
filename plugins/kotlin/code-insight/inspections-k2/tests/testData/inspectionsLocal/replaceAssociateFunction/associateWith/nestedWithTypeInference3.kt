// PROBLEM: Replace 'associate' with 'associateWith'
// FIX: Replace with 'associateWith'
// WITH_STDLIB
sealed class A {
    class B : A() {
        fun toC() = C()
    }
    class C : A()
}

fun transform(
    src: List<A.B>
): Map<A, A.C> {
    return src.associate<caret> { a -> a to a.toC() }
}