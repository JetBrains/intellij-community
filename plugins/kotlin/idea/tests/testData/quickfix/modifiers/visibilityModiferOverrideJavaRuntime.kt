// "Use inherited visibility" "true"
// RUNTIME_WITH_FULL_JDK
// K2_ERROR: Cannot weaken access privilege private for 'findClass' in 'ClassLoader'.
// K2_ERROR: Modifier 'override' is incompatible with 'private'.
// K2_ERROR: Modifier 'private' is incompatible with 'override'.
abstract class C : ClassLoader() {
    <caret>private override fun findClass(var1: String): Class<*> {
        throw ClassNotFoundException(var1)
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.UseInheritedVisibilityFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.UseInheritedVisibilityFix