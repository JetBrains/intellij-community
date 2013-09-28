public class Parent<S> {}

interface I<IT> {
  void method(IT t);
}

class Child<T> extends Parent<T> implements I<T>{
  <caret>
  public void method(T t){}
}
