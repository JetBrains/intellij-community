// "Add documentation" "true"

class C {

    fun <caret>foo() {

    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.kdoc.KDocMissingDocumentationInspection$AddDocumentationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.kdoc.KDocMissingDocumentationInspection$createQuickFix$1