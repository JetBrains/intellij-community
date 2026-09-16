// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -XXLanguage:+FullValueClasses

<caret>value class User(val firstName: String, val lastName: String) {
    fun copy(firstName: String = this.firstName, lastName: String = this.lastName) = User(firstName, lastName)
}
