public class Bug {
  interface I1{}
  interface I2{}
  class C1 {}
  class C2 extends C1 implements I1, I2{}
  class C3 extends C1 implements I1, I2{}

  public static void x(C1 c1) {
    if (c1 instanceof I1 && c1 instanceof I2) {

    }

    if (1 > Math.random() && Math.random()==Math.random()) {

    }

    if (!true){

    }
  }
}
class PointlessBooleanExpression {
  void foo(boolean a, boolean b) {
    boolean c = !(b && false);
    boolean d = a ^ b ^ true;
    boolean x = a ^ !true ^ b;

    boolean y = false || c;
    boolean z = b != true;
  }
}
class Presley {
  void elvis(Object king) {
    if (true && king != null && king.hashCode() > 1) {
      // blah
    }
  }
}