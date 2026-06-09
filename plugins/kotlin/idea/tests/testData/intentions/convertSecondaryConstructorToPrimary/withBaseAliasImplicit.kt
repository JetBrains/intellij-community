// "Convert to primary constructor" "true"
abstract class A<T>

typealias AS = A<String>

class C : AS {
    <caret>constructor()
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.ConvertSecondaryConstructorToPrimaryInspection$createQuickFix$1