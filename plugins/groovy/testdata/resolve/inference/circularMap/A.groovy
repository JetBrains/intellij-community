class A {

  String var1
  B someElse

  Map toMap() {
    [
      var_1: var1,
      thing: someElse.toMap()
    ]
  }

  class B {
    A thing
    B someElse1
    Map toMap() {
      def someName = [
        thing: thing.toMap()
        B someElse1.toMap()
      ]
      someN<ref>ame
    }
  }
}