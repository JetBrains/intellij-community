// "Use inherited visibility" "true"
// RUNTIME_WITH_FULL_JDK
abstract class C : ClassLoader() {
    <caret>private override fun findClass(var1: String): Class<*> {
        throw ClassNotFoundException(var1)
    }
}

/* IGNORE_K2 */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityModifierFix