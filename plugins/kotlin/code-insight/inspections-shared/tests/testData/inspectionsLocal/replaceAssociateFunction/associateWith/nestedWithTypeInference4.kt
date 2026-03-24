// PROBLEM: Replace 'associate' with 'associateWith'
// FIX: Replace with 'associateWith'
// WITH_STDLIB
sealed class A {
    class B : A() {
    }
    class C : A()
}

fun toC(b: A.B) = A.C()

fun transform(
    src: List<A.B>
): Map<A, A.C> {
    return src.associate<caret> { it to toC(it) }
}