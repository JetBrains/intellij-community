class Bug {
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

    if (<warning descr="'!true' can be simplified to 'false'">!true</warning>){

    }
  }
}
class PointlessBooleanExpression {
  void foo(boolean a, boolean b) {
    boolean c = <warning descr="'!(b && false)' can be simplified to 'true'">!(b && false)</warning>;
    boolean d = <warning descr="'a ^ b ^ true' can be simplified to '!(a ^ b )'">a ^ b ^ true</warning>;
    boolean x = <warning descr="'a ^ !true ^ b' can be simplified to 'a ^ b'">a ^ !true ^ b</warning>;

    boolean y = <warning descr="'false || c' can be simplified to 'c'">false || c</warning>;
    boolean z = <warning descr="'b != true' can be simplified to '!b'">b != true</warning>;
  }

  boolean sideEffect() {
    System.out.println("hello");
    return Math.random() > 0.5;
  }

  // side-effect cannot be extracted from field declaration
  boolean field = sideEffect() && false;
  boolean field1 = false & sideEffect();
  // no side-effect extraction necessary
  boolean field2 = <warning descr="'sideEffect() && true' can be simplified to 'sideEffect()'">sideEffect() && true</warning>;
  boolean field3 = <warning descr="'false && sideEffect()' can be simplified to 'false'">false && sideEffect()</warning>;

  void method() {
    if(<warning descr="'sideEffect() && false' can be simplified to 'false'">sideEffect() && false</warning>) {
      System.out.println("ok");
    }
    if(<warning descr="'sideEffect() && true' can be simplified to 'sideEffect()'">sideEffect() && true</warning>) {
      System.out.println("ooh");
    }
    // Do not warn as we cannot simplify w/o reordering calls which could be undesired
    // this code is warned by DFA inspection (w/o quick-fix)
    if(Math.random() > 0.5 && (sideEffect() || true)) {
      System.out.println("well");
    }
    // Here side-effect can be extracted before loop
    if((<warning descr="'sideEffect() || true' can be simplified to 'true'">sideEffect() || true</warning>) && Math.random() > 0.5) {
      System.out.println("well");
    }
  }
}
class Presley {
  void elvis(Object king) {
    if (<warning descr="'true && king != null && king.hashCode() > 1' can be simplified to 'king != null && king.hashCode() > 1'">true && king != null && king.hashCode() > 1</warning>) {
      // blah
    }
  }
}