File foo() {
  List<Integer> ints = []
  if (ints.empty) {
    print {return 42}
    for (x in ints) {
      <warning descr="Cannot assign 'Integer' to 'File'">return 43</warning>
    }
  }
  <warning descr="Cannot assign 'Integer' to 'File'">67</warning>
}