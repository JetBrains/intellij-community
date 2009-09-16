class MyDelegate {
  def saySomething() {
    println "hello!"
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