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
        int i = 0;
        switch(a) {
            case foo:
                i = 1;
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

    void zzz(MyEnum e, int i) {
        String msg;
        switch (e) {
            case foo:
            case bar:
            case baz:
                msg = "M";
                break;
            default:
                return;
        }
        System.out.println(msg);
    }

    void zzzz(MyEnum e) {
        String msg;
        switch (e) {
            case foo:
                msg = "A";
                break;
            case bar:
                msg = "B";
                break;
            case baz:
            <warning descr="'default' branch is unnecessary">default</warning>:
                msg = "M";
        }
        msg = "N";
        System.out.println(msg);
    }

    void lambda(MyEnum e) {
        Runnable r = () -> {
            String msg;
            switch (e) {
                case foo:
                case bar:
                case baz:
                    msg = "M";
                    break;
                <warning descr="'default' branch is unnecessary">default</warning>:
                    return;
            }
        };
    }

    void notAllEnumConstantsCovered(MyEnum e) {
        switch (e) {
            case foo,bar:
                System.out.println(1);
                break;
            default:
                System.out.println(2);
        }
    }

    void brokenCode(MyEnum e) {
        switch (e) {
            case <error descr="Duplicate label 'foo'">foo</error>:
            case <error descr="Duplicate label 'foo'">foo</error>:
            case <error descr="Duplicate label 'foo'">foo</error>:
                System.out.println(9);
            default: break;
        };
    }
}
enum MyEnum{
    foo, bar, baz;
}

