// "Change type argument to String" "true"
// K2_ERROR: RETURN_TYPE_MISMATCH_ON_OVERRIDE

interface Foo<T> { fun foo(): T}

class Outer {
  class String
  class FooImpl: Foo<Int> {
      override fun foo(): kotlin<caret>.String = ""
   }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeSuperTypeListEntryTypeArgumentFixFactory$ChangeSuperTypeListEntryTypeArgumentFix