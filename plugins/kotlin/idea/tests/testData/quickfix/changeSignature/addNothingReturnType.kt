// "Specify return type explicitly" "true"
// WITH_STDLIB
// K2_ERROR: IMPLICIT_NOTHING_RETURN_TYPE
<caret>fun foo() = throw IllegalArgumentException()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.intentions.SpecifyTypeExplicitlyIntention