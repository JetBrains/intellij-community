import com.intellij.openapi.actionSystem.AnAction

class A : B {
  constructor() {
    B(42)
  }
}

open class B : AnAction {
  constructor() : super()
  constructor(@Suppress("UNUSED_PARAMETER") i: Int) : super("Test action text")
}