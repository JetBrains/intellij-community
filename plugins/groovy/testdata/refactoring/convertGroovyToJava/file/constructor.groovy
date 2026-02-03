class Base {
  def Base(def a) {}
}

class Inheritor extends Base {
  def Inheritor(int x, int y){
    super(x)
    print y
  }
}