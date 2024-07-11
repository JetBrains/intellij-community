// "Create subclass" "true"
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION

open class <caret>Base<T : Any, L : List<T>>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.CreateKotlinSubClassIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.CreateKotlinSubClassIntention