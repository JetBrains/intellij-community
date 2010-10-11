class Test {
    function foo() {
        Runnable {
            override function run() {
                Test.this.foo();
            }
        }
    }
}