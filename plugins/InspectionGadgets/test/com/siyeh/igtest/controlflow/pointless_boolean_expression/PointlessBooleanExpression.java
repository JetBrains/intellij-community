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

  class X {
    X(boolean b) {}
  }

  class Y extends X {
    Y(int i) {
      // side-effect cannot be extracted from super call
      super(sideEffect() && false);
    }

    Y(long l) {
      // side-effect cannot be extracted from super call
      super(false & sideEffect());
    }

    Y(double d) {
      // no side-effect extraction necessary
      super(<warning descr="'sideEffect() && true' can be simplified to 'sideEffect()'">sideEffect() && true</warning>);
    }

    Y(float f) {
      // no side-effect extraction necessary
      super(<warning descr="'false && sideEffect()' can be simplified to 'false'">false && sideEffect()</warning>);
    }
  }

  void method() {
    if(<warning descr="'sideEffect() && false' can be simplified to 'false'">sideEffect() && false</warning>) {
      System.out.println("ok");
    }
    if(<warning descr="'sideEffect() && true' can be simplified to 'sideEffect()'">sideEffect() && true</warning>) {
      System.out.println("ooh");
    }
    // Here side-effect can be extracted and placed inside loop
    if(Math.random() > 0.5 && (<warning descr="'sideEffect() || true' can be simplified to 'true'">sideEffect() || true</warning>)) {
      System.out.println("well");
    }
    // Extracting side-effects from conjunction in if statement which contains else branch is still not supported (though warning is displayed)
    if(Math.random() > 0.5 && (sideEffect() || true)) {
      System.out.println("ok");
    } else {
      System.out.println("cancel");
    }
    // Here side-effect can be extracted before loop
    if((<warning descr="'sideEffect() || true' can be simplified to 'true'">sideEffect() || true</warning>) && Math.random() > 0.5) {
      System.out.println("well");
    }
  }

  static int i = 1;
  public static void main(String[] args) {
    boolean b = false;
    if (i == 1 && (<warning descr="'b |= true' can be simplified to 'b=true'">b |= true</warning>)) // side-effects
      System.out.println("i == 1");
    if (i == 1 && (<warning descr="'b |= false' can be simplified to 'b'">b |= false</warning>))
      System.out.println("i == 1");
    if (<warning descr="'b |= false' can be simplified to 'b'">b |= false</warning>)
      System.out.println("i == 1");
    if (<warning descr="'b |= true' can be simplified to 'b=true'">b |= true</warning>)
      System.out.println("i == 1");
    if (b = true)
      System.out.println("i == 1");
    System.out.println(b);

    if (i == 1 && (<warning descr="'b &= true' can be simplified to 'b'">b &= true</warning>)) { }
    if (i == 1 && (<warning descr="'b &= false' can be simplified to 'b=false'">b &= false</warning>)) { }
    if (<warning descr="'b &= true' can be simplified to 'b'">b &= true</warning>) {}
    if (<warning descr="'b &= false' can be simplified to 'b=false'">b &= false</warning>) {}
  }
}
class Presley {
  void elvis(Object king) {
    if (<warning descr="'true && king != null && king.hashCode() > 1' can be simplified to 'king != null && king.hashCode() > 1'">true && king != null && king.hashCode() > 1</warning>) {
      // blah
    }
  }
}

class LambdaInOverloadMethod {
  Boolean myField;
  void m(I<Boolean> i) {}
  void m(IVoid i) {}

  Boolean get() {return myField;}

  {
    m(() -> get() <caret>== true);
  }
}

interface I<T> {
  T f();
}

interface IVoid extends I<Void>{
  void foo();

  @Override
  default Void f() {
    return null;
  }
}