// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:+FullValueClasses
// COMPILER_ARGUMENTS: -Xexplicit-backing-fields

<caret>data class User(val firstName: String, val lastName: String) {
    val nicknames: List<String>
        field = mutableListOf<String>()
}
