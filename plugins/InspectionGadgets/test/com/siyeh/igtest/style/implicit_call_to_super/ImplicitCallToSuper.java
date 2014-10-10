public class ImplicitCallToSuper {

  <warning descr="Implicit call to 'super()'">ImplicitCallToSuper</warning>() {}
}
class A {
  <error descr="Illegal type: 'void'">void</error> () {}
}
class B {
  B() {
    <error descr="Cannot resolve symbol 'sup'">sup</error><EOLError descr="';' expected"></EOLError>
  }
}