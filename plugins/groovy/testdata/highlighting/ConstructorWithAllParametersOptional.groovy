class Base {
  def Base(int x = 0, int y=5){}
}

class Inheritor extends Base{
}

class Base2 {
  def Base2(int x){}
}

<error descr="There is no default constructor available in class 'Base2'">class Inheritor2 extends Base2</error> {

}