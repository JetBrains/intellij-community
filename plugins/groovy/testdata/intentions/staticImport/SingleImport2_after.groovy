import static Util.doSomething

class Util {
  static doSomething(){}
  static doSomething(def a){}
  static doSomethingElse(def a){}
}

doSomething()
doSomething(2)
Util.doSomethingElse(2)