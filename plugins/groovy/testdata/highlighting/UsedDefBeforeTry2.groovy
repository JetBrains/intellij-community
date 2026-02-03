def <warning descr="Method foo is unused">foo</warning>() {
  String foo = 'used';
  try {
    thrower()
    foo = 'used'
    thrower()
    <warning descr="Assignment is not used">foo</warning> = 'unsed'
    foo = 'used'
    thrower()
  }
  finally {
    print foo
  }
}
