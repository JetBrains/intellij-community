import com.intellij.openapi.actionSystem.AnAction

class A : B {
  <warning descr="Action presentation instantiated in the constructor">constructor</warning>() : this(42)
  <warning descr="Action presentation instantiated in the constructor">constructor</warning>(@Suppress("UNUSED_PARAMETER") i: Int) : super()
}

open class B : AnAction {
  constructor() : super("Test action text")
}
