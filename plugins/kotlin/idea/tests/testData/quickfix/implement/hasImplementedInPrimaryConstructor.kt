// "Implement members" "true"
// WITH_STDLIB
abstract class B {
    abstract val p: String?
    abstract fun test()
}

<caret>class MyImpl(
    override val p: String? = null
) : B()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler