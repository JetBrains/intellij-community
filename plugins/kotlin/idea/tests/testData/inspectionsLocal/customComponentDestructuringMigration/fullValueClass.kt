// PROBLEM: none
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax -XXLanguage:+FullValueClasses

value class UserX(val firstName: String, val lastName: String)

fun testUserX(ux: UserX) {
    (val lastName<caret>) = ux
}
