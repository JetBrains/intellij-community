import com.intellij.openapi.actionSystem.AnAction

class A : B {
  <warning descr="Action presentation initialized in the constructor">constructor</warning>() :this(42)
  <warning descr="Action presentation initialized in the constructor">constructor</warning>(<warning descr="[UNUSED_PARAMETER] Parameter 'i' is never used">i</warning>: Int) : super()
}

open class B : AnAction {
  constructor() : super("blah blah blah")
}
