import com.intellij.openapi.actionSystem.AnAction

class <warning descr="Action presentation instantiated in the constructor">A</warning>() : B(42) {
}

open class B : AnAction {
  constructor() : super()
  constructor(@Suppress("UNUSED_PARAMETER") i: Int) : super("Test action text")
}
