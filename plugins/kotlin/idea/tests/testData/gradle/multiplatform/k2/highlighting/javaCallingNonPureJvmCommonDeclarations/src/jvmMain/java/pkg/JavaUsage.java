//region Test configuration
// - hidden: line markers
//endregion
package pkg;

import static pkg.CommonKt.someFunWithString;
import static pkg.CommonKt.block;

public class JavaUsage {
    void test() throws <!HIGHLIGHTING("severity='ERROR'; descr='Incompatible types. Found: 'pkg.MyException', required: 'java.lang.Throwable''")!>MyException<!> {
        throw new <!HIGHLIGHTING("severity='ERROR'; descr='Incompatible types. Found: 'pkg.MyException', required: 'java.lang.Throwable''")!>MyException<!>();
    }

    void test1(String s) {
        someFunWithString<!HIGHLIGHTING("severity='ERROR'; descr=''someFunWithString(java.lang.@org.jetbrains.annotations.NotNull String)' in 'pkg.CommonKt' cannot be applied to '(java.lang.String)''")!>(s)<!>;
        someFunWithString<!HIGHLIGHTING("severity='ERROR'; descr=''someFunWithString(java.lang.@org.jetbrains.annotations.NotNull String)' in 'pkg.CommonKt' cannot be applied to '(java.lang.String)''")!>("")<!>;
    }

    void test2() {
        EXPECT expect = new Provider().<!HIGHLIGHTING("severity='ERROR'; descr='Incompatible types. Found: 'pkg.@org.jetbrains.annotations.NotNull EXPECT', required: 'pkg.EXPECT''")!>getExpect<!>();
    }

    void test3() {
        block(<!HIGHLIGHTING("severity='ERROR'; descr='kotlin.jvm.functions.@org.jetbrains.annotations.NotNull Function1<? super java.lang.@org.jetbrains.annotations.NotNull String,java.lang.@org.jetbrains.annotations.NotNull Integer> is not a functional interface'")!>value -> 2<!>);
    }

    void test4() {
        new MPPSuper().hello();
        new CommonChild().<!HIGHLIGHTING("severity='ERROR'; descr='Cannot resolve method 'hello' in 'CommonChild''")!>hello<!>();
    }
}
