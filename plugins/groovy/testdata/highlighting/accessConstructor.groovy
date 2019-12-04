import p1.MyClass

def outsideUsage() {
  new MyClass()
  new <warning descr="Access to 'MyClass(int)' exceeds its access rights">MyClass</warning>(1)
  new <warning descr="Access to 'MyClass(int, int)' exceeds its access rights">MyClass</warning>(1, 2)
  new <warning descr="Access to 'MyClass(int, int, int)' exceeds its access rights">MyClass</warning>(1, 2, 3)
}

class Inheritor extends MyClass {

  Inheritor() {
    super()
  }

  Inheritor(int i) {
    super(i)
  }

  Inheritor(int i, int j) {
    <warning descr="Access to 'MyClass(int, int)' exceeds its access rights">super</warning>(i, j)
  }

  Inheritor(int i, int j, int k) {
    <warning descr="Access to 'MyClass(int, int, int)' exceeds its access rights">super</warning>(i, j, k)
  }

  def usage() {
    new MyClass()
    new MyClass(1)
    new <warning descr="Access to 'MyClass(int, int)' exceeds its access rights">MyClass</warning>(1, 2)
    new <warning descr="Access to 'MyClass(int, int, int)' exceeds its access rights">MyClass</warning>(1, 2, 3)
  }

  def anonymousUsage() {
    new Runnable() {
      @Override
      void run() {
        new MyClass()
        new MyClass(1)
        new <warning descr="Access to 'MyClass(int, int)' exceeds its access rights">MyClass</warning>(1, 2)
        new <warning descr="Access to 'MyClass(int, int, int)' exceeds its access rights">MyClass</warning>(1, 2, 3)
      }
    }
  }

  static class InheritorNested {
    def usage() {
      new MyClass()
      new MyClass(1)
      new <warning descr="Access to 'MyClass(int, int)' exceeds its access rights">MyClass</warning>(1, 2)
      new <warning descr="Access to 'MyClass(int, int, int)' exceeds its access rights">MyClass</warning>(1, 2, 3)
    }
  }
}
