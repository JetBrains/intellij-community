// "Implement members" "true"
// ENABLE_MULTIPLATFORM
// ERROR: Expected interface 'InterfaceWithVals' has no actual declaration in module light_idea_test_case for JVM

fun TODO(s: String): Nothing = null!!

expect interface InterfaceWithVals {
    fun funInInterface()

    val importantVal: Int
}

class <caret>ChildOfInterface : InterfaceWithVals{
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix