
abstract class BoxingBoxedValue {
  abstract void method(Integer i);

  void anotherMethod() {
    Integer value = 1;

    method(<warning descr="Boxing of already boxed 'value'">Integer.valueOf</warning>(value));
  }
}