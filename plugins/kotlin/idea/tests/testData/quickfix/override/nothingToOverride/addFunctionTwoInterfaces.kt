// "Add function to supertype…" "true"
interface A {}
interface B {}
class C: A, B {
  <caret>override fun foo() {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionToSupertypeFix