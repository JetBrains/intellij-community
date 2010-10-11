class A {

}

class B extends A {
  public var a;
}

function run(args: String[]) {
  var a: A = B {
    a: 3
  };
  (a as B).<ref>a = 5;
}