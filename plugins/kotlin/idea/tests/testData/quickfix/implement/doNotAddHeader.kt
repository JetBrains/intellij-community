// "Implement members" "true"
// ENABLE_MULTIPLATFORM
// ERROR: Expected interface 'InterfaceWithFuns' has no actual declaration in module light_idea_test_case for JVM
// K2_ERROR: Class 'ChildOfInterface' is not abstract and does not implement abstract member:<br>expect fun funInInterface(): Unit

fun TODO(s: String): Nothing = null!!

expect interface InterfaceWithFuns {
    fun funInInterface()
}

class <caret>ChildOfInterface : InterfaceWithFuns{
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix