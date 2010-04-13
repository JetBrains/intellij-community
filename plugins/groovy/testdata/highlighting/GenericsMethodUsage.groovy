class Parent<T> {
  public def doSmth(T p) {}
}

class Child extends Parent<Float> {}

new Parent().doSmth(10f)    // ok
new Child().doSmth(10f)