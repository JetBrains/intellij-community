// "Specify return type explicitly" "true"

fun foo<caret>() = arrayOf(java.lang.String.valueOf(1))
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention