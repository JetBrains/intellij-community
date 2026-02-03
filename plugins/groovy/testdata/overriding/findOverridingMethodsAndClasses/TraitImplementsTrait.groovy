trait Trait {
  abstract void foo()
}

trait Implementor implements Trait {
  void foo(){}
}

class Implementor2 implements Implementor {
  void foo(){}
}