// COMPILER_ARGUMENTS: -Xname-based-destructuring=name-mismatch -XXLanguage:+FullValueClasses
// PROBLEM: Full-form name-based destructuring can use the short form
// FIX: Convert to short name-based destructuring form

value class UserX(val firstName: String, val lastName: String)

fun test(ux: UserX) {
    (<caret>val lastName, val firstName) = ux
    println(firstName + lastName)
}
