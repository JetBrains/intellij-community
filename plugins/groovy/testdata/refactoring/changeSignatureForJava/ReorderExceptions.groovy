class Derived1 extends Test {
    void foo() throws MyException,
       MyException1 {

    }

    void bar () {
        try {
            foo();
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