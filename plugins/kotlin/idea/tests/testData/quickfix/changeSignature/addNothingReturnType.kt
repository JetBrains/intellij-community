// "Specify return type explicitly" "true"
// WITH_STDLIB
<caret>fun foo() = throw IllegalArgumentException()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.SpecifyTypeExplicitlyIntention