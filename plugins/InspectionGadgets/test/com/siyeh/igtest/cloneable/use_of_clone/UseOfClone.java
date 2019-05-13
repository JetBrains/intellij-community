package use_of_clone;

import java.util.concurrent.Callable;

class UseOfClone implements <warning descr="Use of 'Cloneable'">Cloneable</warning> {

  void f(int[] is) {
    System.out.println(is.clone()); // don't warn when cloning an array
  }

  public UseOfClone <warning descr="Implementation of 'clone()'">clone</warning>() {
    return this;
  }

  void g(UseOfClone o) {
    o.<warning descr="Call to 'clone()'">clone</warning>();
  }

  void h() {
    Callable r = <warning descr="Use of 'this::clone'">this::clone</warning>;
  }
}
interface I extends <warning descr="Use of 'Cloneable'">Cloneable</warning> {
}