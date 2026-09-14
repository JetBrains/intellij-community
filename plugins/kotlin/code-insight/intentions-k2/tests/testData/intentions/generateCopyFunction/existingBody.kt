// COMPILER_ARGUMENTS: -XXLanguage:+FullValueClasses

value cla<caret>ss User(val firstName: String, val lastName: String) {
    fun fullName(): String = "$firstName $lastName"
}
