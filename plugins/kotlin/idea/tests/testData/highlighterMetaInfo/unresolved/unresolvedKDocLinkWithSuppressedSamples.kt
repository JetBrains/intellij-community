// FIR_IDENTICAL

// HIGHLIGHT_WARNINGS
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.kdoc.KDocUnresolvedReferenceInspection
// REGISTRY: kotlin.kdoc.should.report.samples false

class A {}

/**
 * [A]
 */
fun resolved() {}

/**
 * bar should be marked as unresolved, but A should not
 * [A.bar]
 */
fun partiallyResolved(){}

/**
 * [unresolvedLink]
 */
fun unresolved() {}

/**
 * @sample samples
 * @sample someRandomName
 */
fun samples() {}
