import static Util.*

class Util {
  static doSomething(){}
  static doSomething(def a){}
  static doSomethingElse(def a){}
}

def doSomethingElse(){}

doSomething()
doSomething(2)
doSomethingElse(2)