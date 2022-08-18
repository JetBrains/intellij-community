class UnnecessaryConditionalExpression {

  void one(boolean condition) {
    final boolean a = <warning descr="'condition ? true : false' can be simplified to 'condition'">condition</warning> ? true : false;
    final boolean b = <warning descr="'condition ? false : true' can be simplified to '!condition'">condition</warning> ? false : true;
  }

  int two(int i) {
    return <warning descr="'i == 0 ? 0 : i' can be simplified to 'i'">i == 0</warning> ? 0 : i;
  }

  Object three(Object o) {
    return <warning descr="'o != null ? o : null' can be simplified to 'o'">o != null</warning> ? o : null;
  }

  int four(int a, int b) {
    return <warning descr="'a == b ? a : b' can be simplified to 'b'">a == b</warning> ? a : b;
  }
  
  boolean or(int a, int b) {
    return <warning descr="'a > b ? true : b == 5' can be simplified to 'a > b || b == 5'">a > b</warning> ? true : b == 5;
  }
  
  boolean and(int a, int b) {
    return <warning descr="'a > b ? false : b == 5' can be simplified to 'a<=b && b == 5'">a > b</warning> ? false : b == 5;
  }
  
  boolean cond(int a, int b, int c) {
    return <warning descr="'a > 0 ? b < c : b >= c' can be simplified to '(a > 0) == (b < c)'">a > 0</warning> ? b < c : b >= c;
  }

  // IDEA-267511
  void confusing() {
    Boolean x = Math.random() > 0.5 ? true : Math.random() > 0.5 ? false : null;
  }
}

class InsideLambdaInOverloadedMethod {
  Boolean myField;
  void m(I<Boolean> i) {}
  void m(IVoid i) {}

  Boolean get() {return myField;}

  {
    m(() -> <warning descr="'get() ? false : true' can be simplified to '!get()'">get()</warning> ? false : true);
    m(() -> get() ? true : false);
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
