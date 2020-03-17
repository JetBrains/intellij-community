import p1.MyClass

def outsideUsage(MyClass mc) {
  mc.publicMethod()
  mc.<warning descr="Access to 'protectedMethod' exceeds its access rights">protectedMethod</warning>()
  mc.<warning descr="Access to 'packageLocalMethod' exceeds its access rights">packageLocalMethod</warning>()
  mc.<warning descr="Access to 'privateMethod' exceeds its access rights">privateMethod</warning>()
}

class Inheritor extends MyClass {

  def usage() {
    publicMethod()
    protectedMethod()
    <warning descr="Access to 'packageLocalMethod' exceeds its access rights">packageLocalMethod</warning>()
    <warning descr="Access to 'privateMethod' exceeds its access rights">privateMethod</warning>()
  }

  def anonymousUsage() {
    new Runnable() {
      @Override
      void run() {
        publicMethod()
        protectedMethod()
        <warning descr="Access to 'packageLocalMethod' exceeds its access rights">packageLocalMethod</warning>()
        <warning descr="Access to 'privateMethod' exceeds its access rights">privateMethod</warning>()
      }
    }
  }

  class InheritorInner {
    def usage() {
      publicMethod()
      protectedMethod()
      <warning descr="Access to 'packageLocalMethod' exceeds its access rights">packageLocalMethod</warning>()
      <warning descr="Access to 'privateMethod' exceeds its access rights">privateMethod</warning>()
    }
  }

  static class InheritorNested {
    def usage(MyClass mc) {
      mc.publicMethod()
      mc.protectedMethod()
      mc.<warning descr="Access to 'packageLocalMethod' exceeds its access rights">packageLocalMethod</warning>()
      mc.<warning descr="Access to 'privateMethod' exceeds its access rights">privateMethod</warning>()
    }
  }
}
