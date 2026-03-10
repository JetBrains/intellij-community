// "Change type argument to String" "true"
// K2_ERROR: Type of 'val x: String' is not a subtype of overridden property 'val x: Int' defined in 'Outer.FooImpl'.

interface Foo<T> { val x: T}

class Outer {
  class String
  class FooImpl: Foo<Int> {
      override val x : kotlin<caret>.String = ""
   }
}
// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeSuperTypeListEntryTypeArgumentFixFactory$ChangeSuperTypeListEntryTypeArgumentFix