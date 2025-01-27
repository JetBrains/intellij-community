// "Convert to primary constructor" "true"
interface Interface

interface Another

abstract class Base

class Derived : Interface, Base, Another {

    val x: String

    constructor(x: String<caret>): super() {
        this.x = x
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ConvertSecondaryConstructorToPrimaryInspection$createQuickFixes$1