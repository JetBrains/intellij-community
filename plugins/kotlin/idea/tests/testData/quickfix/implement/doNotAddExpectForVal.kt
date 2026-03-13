// "Implement members" "true"
// ENABLE_MULTIPLATFORM
// ERROR: Expected interface 'InterfaceWithVals' has no actual declaration in module light_idea_test_case for JVM
// K2_ERROR: Class 'ChildOfInterface' is not abstract and does not implement abstract members:<br>expect fun funInInterface(): Unit<br>expect val importantVal: Int

fun TODO(s: String): Nothing = null!!

expect interface InterfaceWithVals {
    fun funInInterface()

    val importantVal: Int
}

class <caret>ChildOfInterface : InterfaceWithVals{
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix