class MyDelegate {
  def saySomething(String str) {
    println str
  }
}

class Runner {
  def boo(obj, Closure cl) {
    cl.delegate = obj
    cl()
  }
}

def runner = new Runner()
runner.boo(new MyDelegate()) {
  saySomething(<caret>)
}