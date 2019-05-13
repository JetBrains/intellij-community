trait Trait {
  abstract void foo()
}

class Implementor implements Trait {
  void foo(){}
}

class Implementor2 extends Implementor {
  void foo(){}
}