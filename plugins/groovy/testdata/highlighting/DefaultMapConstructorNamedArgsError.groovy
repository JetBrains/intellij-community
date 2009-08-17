class X {
  def setX(int x) {}

  private y;
}

class Y extends X {
  double a;
  def getB(){}
}

print new Y(x: 5, <warning descr="Property 'f' does not exist">f</warning>:6, y: new Object(), a: 5.8, <warning descr="Property 'b' does not exist">b</warning>:9);

