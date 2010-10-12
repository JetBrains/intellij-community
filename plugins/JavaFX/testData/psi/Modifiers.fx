 public var a = 1;
 protected var b = 2;
 package var c = 3;
 public-read var e = 5;
 package public-read var f = 6;
 public-read package var g = 7;

 public function foo1() {}
 package function foo2() {}
 protected function foo3() {}

 public class A {
     public-init var a: String;
     protected public-init var b: Integer;
     package public-init var c: Number;

     protected function foo() {}
 }

 protected class B extends A {
    override package var b = 4 on replace old {print("{old}");};

     public override function foo() {};
 }

 mixin class M {
  private function foo() {}
 }
