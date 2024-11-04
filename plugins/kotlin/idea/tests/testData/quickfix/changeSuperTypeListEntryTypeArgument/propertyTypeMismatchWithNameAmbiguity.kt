// "Change type argument to String" "true"

interface Foo<T> { val x: T}

class Outer {
  class String
  class FooImpl: Foo<Int> {
      override val x : kotlin<caret>.String = ""
   }
}
// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeSuperTypeListEntryTypeArgumentFixFactory$ChangeSuperTypeListEntryTypeArgumentFix