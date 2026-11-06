// "Add empty brackets after primary constructor" "true"
// K2_ERROR: SYNTAX

class Fruit private constructor<caret> {
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MissingConstructorBracketsFix