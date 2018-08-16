import com.intellij.openapi.components.BaseComponent

class <warning descr="Application Component is not registered in plugin.xml">Unregistered<caret>BaseComponent</warning>
  : BaseComponent, UnregisteredApplicationComponentInterface {

  class <warning descr="Application Component is not registered in plugin.xml">InnerStaticClassApplicationContext</warning>
  : BaseComponent

  inner class InnerClassApplicationContextIsNotChecked : BaseComponent

  fun getInstance() : UnregisteredApplicationComponentInterface? = null
}