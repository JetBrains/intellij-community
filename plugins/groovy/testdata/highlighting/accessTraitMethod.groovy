trait T {
  private traitMethod() {
    42
  }
}

class SomeClass implements T {}

new SomeClass().<warning descr="Access to 'traitMethod' exceeds its access rights">traitMethod</warning>()
