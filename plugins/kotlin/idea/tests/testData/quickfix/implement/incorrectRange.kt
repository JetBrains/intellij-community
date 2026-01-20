// "class org.jetbrains.kotlin.idea.intentions.CreateKotlinSubClassIntention" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix" "false"
// ACTION: Convert function to property
// ACTION: Convert member to extension

interface Base {
    fun <caret>foo(): Int
}