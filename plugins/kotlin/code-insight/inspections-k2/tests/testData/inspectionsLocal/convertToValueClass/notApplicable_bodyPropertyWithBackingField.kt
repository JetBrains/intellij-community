// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:+FullValueClasses

<caret>data class User(val firstName: String, val lastName: String) {
    val fullName: String = "$firstName $lastName"
}
