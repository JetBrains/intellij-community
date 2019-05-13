import com.intellij.openapi.components.BaseComponent

class <warning descr="Component is not registered in plugin.xml">UnregisteredApplicationComponent</warning>
  : BaseComponent, UnregisteredApplicationComponentInterface {

  class <warning descr="Component is not registered in plugin.xml">InnerStaticClassApplicationContext</warning>
  : BaseComponent

  inner class InnerClassApplicationContextIsNotChecked : BaseComponent

  fun getInstance() : UnregisteredApplicationComponentInterface? = null
}