public class Test {
    function foo() {
        Runnable {
            public override function run() {
                this.ba<ref>r();
            }
            function bar() {
              print("Runnable.bar()")
            }
        }
    }
    public function bar() {
        print("Test.bar()")
    }
}