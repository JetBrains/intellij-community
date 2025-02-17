// "Convert to primary constructor" "true"
interface A<T>

typealias AS = A<String>

class C : AS {
    <caret>constructor()
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ConvertSecondaryConstructorToPrimaryInspection$createQuickFix$1