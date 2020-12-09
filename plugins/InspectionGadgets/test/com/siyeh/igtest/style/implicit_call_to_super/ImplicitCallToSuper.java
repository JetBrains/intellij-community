public class ImplicitCallToSuper {

  <warning descr="Implicit call to 'super()'">ImplicitCallToSuper</warning>() {}
}
class A {
  <error descr="Identifier expected">void</error> () {}
}
class B {
  B() {
    <error descr="Cannot resolve symbol 'sup'">sup</error><EOLError descr="';' expected"></EOLError>
  }

  <warning descr="Implicit call to 'super()'">B</warning>(int i) {
    System.out.println(i);
    System.out.println(i);
  }
}
enum X {
  ;
  X() {}
}
record Y() {
  public Y() {}
}