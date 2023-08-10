package testing.kt

open class KotlinBase {
  open var <caret>some = "Test"
}

// REF: of testing.jj.JavaBase.getSome()
// REF: of testing.jj.JavaBase.setSome(String)