// FIR_IDENTICAL
// HIGHLIGHTER_ATTRIBUTES_KEY
// CHECK_SYMBOL_NAMES

// FILE: useSite.kt

fun s(m: MyJavaClass) {
    //fields
    m.isFinalBool
    m.isBool

    //fields with getters
    m.isWithGetterBool
    m.isWithGetterAndSetterBool
    m.isWithGetterAndSetterBool = true
}

// FILE: MyJavaClass.java
public class MyJavaClass {

    final public boolean isFinalBool = false;
    public boolean isBool;

    private boolean withGetterBool;

    private boolean withGetterAndSetterBool;


    public boolean isWithGetterBool() {
        return withGetterBool;
    }

    public boolean isWithGetterAndSetterBool() {
        return withGetterAndSetterBool;
    }

    public void setWithGetterAndSetterBool(boolean withGetterAndSetterBool) {
        this.withGetterAndSetterBool = withGetterAndSetterBool;
    }
}