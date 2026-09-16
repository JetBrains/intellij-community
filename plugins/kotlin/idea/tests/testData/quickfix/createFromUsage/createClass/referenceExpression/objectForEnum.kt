// "Create object 'RED'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE
enum class SampleEnum {}

fun usage() {
    SampleEnum.RED<caret>
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction