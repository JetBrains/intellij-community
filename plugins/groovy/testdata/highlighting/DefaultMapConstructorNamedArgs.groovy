class X {
  def setX(int x) {}

  private y;
}

class Y extends X {
  double a;
}

print new Y(x: 5, y: new Object(), a: 5.8);

print new Y([x: 5])
print new Y(x: 5)
print new Y<warning descr="Cannot apply default constructor for class 'Y'">([x: 5],y: 5)</warning>

