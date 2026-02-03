// "class org.jetbrains.kotlin.idea.intentions.CreateKotlinSubClassIntention" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix" "false"

fun foo() {
    abstract class <caret>My {
        abstract fun bar(): Int
    }
}

