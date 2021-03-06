public class UnnecessaryConstructor {
  public <warning descr="No-arg constructor 'UnnecessaryConstructor()' is redundant">UnnecessaryConstructor</warning>() {
    super ();
  }
}
class A {
  <error descr="Identifier expected">void</error> () {}
}

enum En {
  EnC;
  private <warning descr="No-arg constructor 'En()' is redundant">En</warning>() {}
}