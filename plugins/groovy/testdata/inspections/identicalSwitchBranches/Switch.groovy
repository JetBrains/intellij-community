int n = 1
switch (n) {
  case <warning descr="Duplicate switch case '1'">1</warning>:
    System.out.println(n)
    break
  case <warning descr="Duplicate switch case '1'">1</warning>:
    System.out.println(n)
    break
  default:
    System.out.println("default")
}