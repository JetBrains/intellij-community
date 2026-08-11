// RUNTIME_WITH_FULL_JDK
// "Add link qualifier" "true"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.kdoc.KDocUnresolvedReferenceInspection

/**
 * [invariant<caret>SeparatorsPathString]
 */
fun aaa(){}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.KDocUnresolvedLinkQuickFix
