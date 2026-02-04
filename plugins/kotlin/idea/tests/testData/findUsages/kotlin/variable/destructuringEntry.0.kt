// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
// OPTIONS: usages
// IGNORE_K1
// CRI_IGNORE
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

data class Person(val fullName: String, val age: Int)

fun test(person: Person) {
    (val <caret>a = fullName, val b = age) = person
    println(a)
}
