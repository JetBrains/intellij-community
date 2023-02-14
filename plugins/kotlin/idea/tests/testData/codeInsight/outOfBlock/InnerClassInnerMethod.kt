// OUT_OF_CODE_BLOCK: FALSE
// TYPE: '\b"a"'
// INSPECTION-CLASS: org.jetbrains.kotlin.idea.inspections.RedundantInnerClassModifierInspection
// INSPECTION: [GENERIC_ERROR_OR_WARNING:6] Redundant 'inner' modifier
class RedundantInner(private val s: String) {
    private inner class D {
        fun f(): Int {
            return s<caret>.length
        }
    }
}