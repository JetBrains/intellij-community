try (def a = System.out) {
  println a
}

try (def b) {
  println <warning descr="Variable 'b' might not be assigned">b</warning>
}
