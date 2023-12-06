// "Remove initializer from property" "true"
abstract class A {
    abstract var i = 0<caret>
}

/* IGNORE_K2 */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePartsFromPropertyFix