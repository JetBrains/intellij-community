class MyClass {

  private privateField
  private privateMethod() {}
  private MyClass() {}

  def usage() {
    privateField
    privateMethod()
    new MyClass()
  }

  def anonymousUsage() {
    new Runnable() {
      @Override
      void run() {
        privateField
        privateMethod() // https://issues.apache.org/jira/browse/GROOVY-9328
        new MyClass()   // https://issues.apache.org/jira/browse/GROOVY-9328
      }
    }
  }

  class Inner {
    def usage() {
      privateField
      privateMethod()
      new MyClass()
    }
  }

  static class Nested {
    def usage(MyClass mc) {
      mc.privateField
      mc.privateMethod()
      new MyClass()
    }
  }
}

class Outer {

  static class MyClass {
    private privateField
    private privateMethod() {}
    private MyClass() {}
  }

  def usage(MyClass mc) {
    mc.privateField
    mc.privateMethod()
    new MyClass()
  }

  class Inner {
    def usage(MyClass mc) {
      mc.privateField
      mc.privateMethod()
      new MyClass()
    }
  }

  static class Nested {
    def usage(MyClass mc) {
      mc.privateField
      mc.privateMethod()
      new MyClass()
    }
  }
}
