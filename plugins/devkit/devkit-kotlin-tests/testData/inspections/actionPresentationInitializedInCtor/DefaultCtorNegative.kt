import com.intellij.openapi.actionSystem.AnAction;

class A : B(42)

abstract class B : AnAction {
  constructor() : super("Test action text")
  constructor(@Suppress("UNUSED_PARAMETER") i: Int) : super()
}
