class Derived1 extends Test {
    void foo() throws MyException1, MyException {

    }

    void bar () {
        try {
            Test.foo();
        }
        catch (MyException e) {}
        catch (MyException1 myException1) {}
    }
}

try {
  new Test().foo();
}
catch (MyException e) {}
catch (MyException1 myException1) {}