class A {
  @org.junit.jupiter.api.extension.RegisterExtension
  Rule5 <warning descr="A.Rule5 should implement org.junit.jupiter.api.extension.Extension">myRule5</warning> = new Rule5();

  class Rule5 {}
}