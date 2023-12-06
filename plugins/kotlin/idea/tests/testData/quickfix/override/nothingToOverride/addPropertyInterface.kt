// "Add 'abstract val hoge: Int' to 'Foo'" "true"
interface Foo

class Bar: Foo {
    override<caret> val hoge = 3
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddPropertyToSupertypeFix