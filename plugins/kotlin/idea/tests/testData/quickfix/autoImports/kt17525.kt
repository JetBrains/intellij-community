// "class org.jetbrains.kotlin.idea.quickfix.ImportConstructorReferenceFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix" "false"
// ERROR: Unresolved reference: ItsInner
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE


class WithInner {
    inner class ItsInner
}

fun referInner(p: WithInner.ItsInner) {
    val v = p::ItsInner<caret>()
}