package com.siyeh.igtest.verbose;

public class UnnecessaryDefault{
    void foo(MyEnum var){
        switch(var){
            case foo:
            case bar:
            case baz:
            <warning descr="'default' branch is unnecessary">default</warning>:
                break;
        }
    }

    int x(MyEnum myEnum) {
        switch (myEnum)
        {
            case foo:
                return 1;
            case bar:
                return 2;
            case baz:
                return 3;
            default:      // << default is never executed because the null value would throw an NPE and all possibilities of the enum are included as cases.
                return 0; //    therefor, a warning is shown that the default branch is unnecessary
        }
    }

    int x1(MyEnum myEnum) {
        switch (myEnum)
        {
            case foo:
                return 1;
            case bar:
                return 2;
            default:
            case baz:
                return 3;
        }
    }

    void y(MyEnum value) {
        switch (value) {
            case foo:
                // process one
                break;
            case bar:
                // process two
                break;
            case baz:
                break;
            default:
                throw new AssertionError(value);
        }
    }

    void z(MyEnum a) {
        String msg;
        switch(a) {
            case foo:
                msg = "X";
                break;
            case bar:
                msg = "Y";
                break;
            case baz:
                msg = "Z";
                break;
            default:
                System.out.println((msg) = null);
        }
        System.out.println("MSG = "+msg);
    }

    void zz(MyEnum a) {
        String msg;
        msg = "A";
        switch(a) {
            case foo:
                msg = "X";
                break;
            case bar:
                msg = "Y";
                break;
            case baz:
                msg = "Z";
                break;
            <warning descr="'default' branch is unnecessary">default</warning>:
                System.out.println((msg) = null);
        }
        System.out.println("MSG = "+msg);
    }
}
enum MyEnum{
    foo, bar, baz;
}

