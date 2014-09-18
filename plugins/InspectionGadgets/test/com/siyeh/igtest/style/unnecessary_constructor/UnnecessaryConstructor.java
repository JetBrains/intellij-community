public class UnnecessaryConstructor {
  public <warning descr="No-arg constructor 'UnnecessaryConstructor()' is redundant">UnnecessaryConstructor</warning>() {
    super ();
  }
}
class A {
  <error descr="Illegal type: 'void'">void</error> () {}
}