public class UnnecessaryConstructor {
  public <warning descr="No-arg constructor 'UnnecessaryConstructor()' is redundant">UnnecessaryConstructor</warning>() {
    super ();
  }
}
class A {
  <error descr="Illegal type: 'void'">void</error> () {}
}

enum En {
  EnC;
  private <warning descr="No-arg constructor 'En()' is redundant">En</warning>() {}
}