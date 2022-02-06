public class UnnecessaryConstructor {
  <warning descr="No-arg constructor 'public UnnecessaryConstructor() { super (); }' is redundant">public UnnecessaryConstructor() {
    super ();
  }</warning>
}
class A {
  <error descr="Identifier expected">void</error> () {}
}

enum En {
  EnC;
  <warning descr="No-arg constructor 'private En() {}' is redundant">private En() {}</warning>
}