// PLATFORM: common
// FILE: common.kt

package pack

class CommonClass

// PLATFORM: jvm
// FILE: Ktij28461.java

package pack;

public class Ktij28461 {
    public static <K> void jGeneric(K a, K b) {
    }

    public static void cstOfJavaPrimitiveAndCommonClass(CommonClass cc) {
        jGeneric<error descr="'jGeneric(K, K)' in 'pack.Ktij28461' cannot be applied to '(int, pack.CommonClass)'">(1, cc)</error>;
    }
}
