class MyDelegate {
  def saySomething(String str) {
    println str
  }
}

class Runner {
  def boo(c, Closure cl) { }
}

def runner = new Runner()
runner.boo new MyDelegate(), {
  saySomething <caret>
}