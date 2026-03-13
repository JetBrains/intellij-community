// "Remove 'inline' modifier" "true"
// K2_ERROR: Public-API inline function cannot access non-public-API property.
class C {
    private var foo = false

    inline fun bar(baz: () -> Unit) {
        if (foo<caret>) {
            baz()
        }
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeModifiersFix