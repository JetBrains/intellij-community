class Util {
  static doSomething(){}
  static doSomething(def a){}
  static doSomethingElse(def a){}
}

Util.doSome<caret>thing()
Util.doSomething(2)
Util.doSomethingElse(2)