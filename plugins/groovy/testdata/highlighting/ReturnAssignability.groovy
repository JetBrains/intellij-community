File foo() {
  List<Integer> ints = []
  if (ints.empty) {
    print {return 42}
    for (x in ints) {
      return <warning descr="Cannot assign 'Integer' to 'File'">43</warning>
    }
  }
  <warning descr="Cannot assign 'Integer' to 'File'">67</warning>
}

File bar() {
  def x = [1, 2, 3]
  if (2 in x) {}
}
