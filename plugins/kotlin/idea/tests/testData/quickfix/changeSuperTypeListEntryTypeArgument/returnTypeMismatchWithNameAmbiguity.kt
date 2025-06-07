// "Change type argument to String" "true"

interface Foo<T> { fun foo(): T}

class Outer {
  class String
  class FooImpl: Foo<Int> {
      override fun foo(): kotlin<caret>.String = ""
   }
}
// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeSuperTypeListEntryTypeArgumentFixFactory$ChangeSuperTypeListEntryTypeArgumentFix