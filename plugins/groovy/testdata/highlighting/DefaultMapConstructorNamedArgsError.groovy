class X {
  def setX(int x) {}

  private y;
}

class Y extends X {
  double a;
  def getB(){}
}

print new Y(x: 5, <error descr="Property 'f' does not exist">f</error>:6, y: new Object(), a: 5.8, <error descr="Property 'b' does not exist">b</error>:9);

