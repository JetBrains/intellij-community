function foo() {
  3
}

function bar(a: Integer, b: Integer): Integer {
  a + b
}

public function run(args: String[]) {
  bar(foo(), foo())
}