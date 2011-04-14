class Base {
  def Base(int x = 0, int y=5){}
}

class Inheritor extends Base{
}

class Base2 {
  def Base2(int x){}
}

class <error descr="There is no default constructor available in class 'Base2'">Inheritor2</error> extends Base2 {

}