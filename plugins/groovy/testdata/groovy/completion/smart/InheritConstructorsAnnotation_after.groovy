import groovy.transform.InheritConstructors

class Base {
  def Base(Date x) {}
}

@InheritConstructors
class Inheritor extends Base {

}

Inheritor i = new Inheritor(<caret>)