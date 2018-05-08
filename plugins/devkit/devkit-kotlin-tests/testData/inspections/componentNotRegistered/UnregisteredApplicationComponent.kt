import com.intellij.openapi.components.ApplicationComponent

class <warning descr="Application Component is not registered in plugin.xml">Unregistered<caret>ApplicationComponent</warning>
  : ApplicationComponent, UnregisteredApplicationComponentInterface {

  class <warning descr="Application Component is not registered in plugin.xml">InnerStaticClassApplicationContext</warning>
  : ApplicationComponent

  inner class InnerClassApplicationContextIsNotChecked : ApplicationComponent

  fun getInstance() : UnregisteredApplicationComponentInterface? = null
}