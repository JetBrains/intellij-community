// "Convert to primary constructor" "true"
abstract class Base(val x: String)

class Derived : Base {
    constructor(x: String<caret>): super(x)
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ConvertSecondaryConstructorToPrimaryInspection$createQuickFixes$1