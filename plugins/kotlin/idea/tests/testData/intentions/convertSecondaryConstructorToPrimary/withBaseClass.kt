// "Convert to primary constructor" "true"
abstract class Base(val x: String)

class Derived : Base {
    constructor(x: String<caret>): super(x)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.ConvertSecondaryConstructorToPrimaryInspection$createQuickFix$1