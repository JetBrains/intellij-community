import com.intellij.openapi.actionSystem.AnAction

class A : B {
  <warning descr="Action presentation instantiated in the constructor">constructor</warning>() {
    B(42)
  }
}

open class B : AnAction {
  constructor() : super("Test action text")
  constructor(@Suppress("UNUSED_PARAMETER") i: Int) : super()
}