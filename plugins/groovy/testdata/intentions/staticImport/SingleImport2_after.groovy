import static Util.doSomething

class Util {
  static doSomething(){}
  static doSomething(def a){}
  static doSomethingElse(def a){}
}

doSome<caret>thing()
doSomething(2)
Util.doSomethingElse(2)