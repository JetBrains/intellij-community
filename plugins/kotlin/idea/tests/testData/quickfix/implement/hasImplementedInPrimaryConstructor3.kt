// "Implement members" "true"
// WITH_STDLIB
abstract class C {
    abstract val p: String?
    abstract val q: Int
    abstract fun test()
}

<caret>class MyImpl3(
    override val p: String? = null
) : C() {
    override val q: Int = 0
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix