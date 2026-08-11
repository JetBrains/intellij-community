// "Change type argument to String" "true"
// K2_ERROR: PROPERTY_TYPE_MISMATCH_ON_OVERRIDE

interface Foo<T> { val x: T}

class Outer {
  class String
  class FooImpl: Foo<Int> {
      override val x : kotlin<caret>.String = ""
   }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeSuperTypeListEntryTypeArgumentFixFactory$ChangeSuperTypeListEntryTypeArgumentFix