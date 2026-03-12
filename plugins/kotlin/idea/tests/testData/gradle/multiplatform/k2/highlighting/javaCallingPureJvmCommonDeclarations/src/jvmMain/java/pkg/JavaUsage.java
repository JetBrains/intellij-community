//region Test configuration
// - hidden: line markers
//endregion
package pkg;

import static pkg.CommonKt.someFunWithString;
import static pkg.CommonKt.block;

public class JavaUsage {
    void test() throws MyException {
        throw new MyException();
    }

    void test1(String s) {
        someFunWithString(s);
        someFunWithString("");
    }

    void test2() {
        EXPECT expect = new Provider().<!HIGHLIGHTING("severity='ERROR'; descr='Incompatible types. Found: 'pkg.@org.jetbrains.annotations.NotNull EXPECT', required: 'pkg.EXPECT''")!>getExpect<!>();
    }

    void test3() {
        block(value -> 2);
    }

    void test4() {
        new MPPSuper().hello();
        new CommonChild().<!HIGHLIGHTING("severity='ERROR'; descr='Cannot resolve method 'hello' in 'CommonChild''")!>hello<!>();
    }
}
