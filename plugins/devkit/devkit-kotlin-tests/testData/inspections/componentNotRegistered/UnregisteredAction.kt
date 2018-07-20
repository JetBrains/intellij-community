import com.intellij.openapi.actionSystem.AnAction

class <warning descr="Action is not registered in plugin.xml">Unregistered<caret>Action</warning> : AnAction() {
  class <warning descr="Action is not registered in plugin.xml">InnerAction</warning> : AnAction()

  protected class NonPublicIsIgnored : AnAction()
}