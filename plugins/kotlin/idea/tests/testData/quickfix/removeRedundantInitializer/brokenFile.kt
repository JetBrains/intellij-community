// "Remove redundant initializer" "true"
// WITH_STDLIB
// ERROR: A 'return' expression required in a function with a block body ('{...}')
// ERROR: Unresolved reference: abc
// ERROR: Unresolved reference: abc
class KTest {
    private fun test(urlMapping: String?): String {
        if (urlMapping == null) return ""
        var urlPattern = urlMapping<caret>.substring(123)
        urlPattern = abc
        urlPattern = abc(urlPattern, 1)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveRedundantInitializerFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.VariableInitializerIsRedundantInspection$createQuickFix$1