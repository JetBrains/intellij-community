// "Implement members" "true"
// WITH_STDLIB

class<caret> C : java.io.Writer(), java.lang.Appendable

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix
