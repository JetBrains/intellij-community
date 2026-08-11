// "Add 'open val hoge: Int' to 'Foo'" "true"
// K2_ERROR: NOTHING_TO_OVERRIDE
open class Foo

class Bar: Foo() {
    override<caret> val hoge = 3
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddPropertyToSupertypeFix

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddPropertyToSupertypeFix