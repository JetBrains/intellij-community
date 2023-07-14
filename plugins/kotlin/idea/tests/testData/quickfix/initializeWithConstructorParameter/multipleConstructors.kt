// "Initialize with constructor parameter" "true"

class User {
    constructor()
    constructor(blah: String)

    val userN<caret>ame: String
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$InitializeWithConstructorParameter