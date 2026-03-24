// PROBLEM: Replace 'associate' with 'associateWith'
// FIX: Replace with 'associateWith'
// WITH_STDLIB
// IGNORE_K1
package somepackage.subpackage2

sealed class A {
    class B : A() {
        fun toC() = C()
    }

    class C : A()

    fun transform(
        src: List<somepackage.subpackage.A.B>
    ): Map<somepackage.subpackage.A, somepackage.subpackage.A.C> {
        return src.associate<caret> { it to it.toC() }
    }
}
