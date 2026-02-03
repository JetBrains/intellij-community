abstract class Abs {
  def Abs(int x){}
  def Abs(int x, int y){}
}

print new Abs<warning descr="Constructor 'Abs' in 'Abs' cannot be applied to '()'">()</warning>{}