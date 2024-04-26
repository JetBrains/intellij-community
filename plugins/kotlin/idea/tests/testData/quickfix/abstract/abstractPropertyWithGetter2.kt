// "Remove getter and initializer from property" "true"
abstract class B {
    abstract val i = <caret>0
        get() = field
}
/* IGNORE_K2 */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePartsFromPropertyFix