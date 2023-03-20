<warning descr="Modifier 'final' is redundant for records">final</warning> record R() {
}

class C {
  <warning descr="Modifier 'static' is redundant for inner records">static</warning> record R () {
  }

  void test() {
    <error descr="Modifier 'static' not allowed here">static</error> <warning descr="Modifier 'final' is redundant for records">final</warning> record R () {
    }
  }
}