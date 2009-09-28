package com.siyeh.igtest.verbose;

import com.siyeh.igtest.bugs.MyEnum;

public class UnnecessaryDefault{
    void foo(){
        MyEnum var = MyEnum.foo;
        switch(var){
            case foo:
            case bar:
            case baz:
            default:
                break;
        }
    }
}
