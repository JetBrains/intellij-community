// "Implement members" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// WITH_STDLIB
// K2_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
// Issue: KTIJ-39837

class Session

interface ContextProvider<E : Any, C : Any> {
    context(session: Session)
    fun prepareContext(element: E): C?
}

abstract class InspectionBase<E : Any, C : Any> : ContextProvider<E, C> {
    abstract class Simple<E : Any, C : Any> : InspectionBase<E, C>()
}

internal class<caret> MyInspection : InspectionBase.Simple<String, MyInspection.Info>() {
    data class Info(val allUnused: Boolean)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix
