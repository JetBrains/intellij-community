// OUT_OF_CODE_BLOCK: FALSE
// TYPE: '\b"a"'
// INSPECTION-CLASS: org.jetbrains.kotlin.idea.inspections.RedundantInnerClassModifierInspection
// INSPECTION: [LIKE_UNUSED_SYMBOL:6] Redundant 'inner' modifier
class RedundantInner(private val s: String) {
    private inner class D {
        fun f(): Int {
            return s<caret>.length
        }
    }
}