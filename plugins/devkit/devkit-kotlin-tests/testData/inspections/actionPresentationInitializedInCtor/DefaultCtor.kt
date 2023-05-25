import com.intellij.openapi.actionSystem.AnAction;

class <warning descr="Action presentation initialized in the constructor">A</warning> : B()

abstract class B : AnAction {
  constructor() : super("blah blah blah")
}
