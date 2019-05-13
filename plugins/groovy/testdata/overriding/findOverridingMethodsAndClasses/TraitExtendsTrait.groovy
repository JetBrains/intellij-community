trait Trait {
  abstract void foo()
}

trait T2 extends Trait {
  void foo() {}
}
class Implementor2 implements T2 {
  void foo(){}
}