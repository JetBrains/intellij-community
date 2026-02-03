File foo() {
  List<Integer> ints = []
  if (ints.empty) {
    print {return 42}
    for (x in ints) {
      <warning descr="Cannot return 'Integer' from method returning 'File'">return</warning> 43
    }
  }
  <warning descr="Cannot return 'Integer' from method returning 'File'">67</warning>
}

File bar() {
  def x = [1, 2, 3]
  if (2 in x) {}
}
