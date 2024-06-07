// Issue: KTIJ-28461
// PLATFORM: common
// FILE: pack/CommomVarArg.kt

package pack
class CommonClassForVA
class CommonVarArgDecl {
    fun <T> ktFunVarargGeneric(vararg p: T) {}
}

// PLATFORM: jvm
// FILE: pack/JvmVarArg.kt

package pack
class JvmClassForVA
class JvmVarArgDecl {
    fun <T> ktFunVarargGeneric(vararg p: T) {}
}

// FILE: pack/JavaReference.java

package pack;
public class JavaReference {
    public void referVarArg(
    CommonVarArgDecl cd, CommonClassForVA cc,
    JvmVarArgDecl jd, JvmClassForVA jc
    ) {
        cd.ktFunVarargGeneric(1, "");
        cd.ktFunVarargGeneric(1, cc);
        cd.ktFunVarargGeneric(1, jc);

        jd.ktFunVarargGeneric(1, "");
        jd.ktFunVarargGeneric<error descr="'ktFunVarargGeneric(T...)' in 'pack.JvmVarArgDecl' cannot be applied to '(int, pack.CommonClassForVA)'">(1, cc)</error>; // Error is here.
        jd.ktFunVarargGeneric(1, jc);
    }
}
