// "class org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix" "false"
// For K2, see KTIJ-33124 K2: Fix leftovers after porting ChangeCallableReturnTypeFix$ForOverridden
// ERROR: Return type of 'foo' is not a subtype of the return type of the overridden member 'public abstract fun foo(): Int defined in A'
// K2_AFTER_ERROR: Return type of 'foo' is not a subtype of the return type of the overridden member 'fun foo(): Int' defined in 'A'.
interface A {
    fun foo(): Int
}

interface B {
    fun foo(): String
}

interface C : A, B {
    override fun foo(): <caret>Long
}

