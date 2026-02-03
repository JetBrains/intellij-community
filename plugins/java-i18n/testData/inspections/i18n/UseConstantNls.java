package com.intellij.openapi.util;

import org.jetbrains.annotations.Nls;
import java.lang.annotation.*;

class X {
    static final String CONSTANT = "Value";
    static final String EMPTY = " ";
    
    void test() {
        use(<warning descr="Reference to non-localized string is used where localized string is expected">CONSTANT</warning>);
        use(EMPTY);
    }
    
    void testParameter(String s) {
        use(<warning descr="Reference to non-localized string is used where localized string is expected">s</warning>);
    }

    void testCall() {
        use(<warning descr="Reference to non-localized string is used where localized string is expected">getNonAnnotated()</warning>);
        use(<warning descr="Reference to non-localized string is used where localized string is expected">this.getNonAnnotated()</warning>);
    }
    
    void testNlsSafe() {
        use(getName());
    }
    
    void testTernary() {
        use(getNonAnnotated() == null ? getName() : EMPTY);
    }
    
    native @NlsSafe String getName();
    
    void testNested(String s) {
        use(nlsResult(s.trim()));
    }
    
    @Nls String getNested(String s) {
        return X.nlsResultStatic(s.trim());
    }
    
    native String getNonAnnotated();
    
    void use(@Nls String c) {}
    
    native static @Nls String nlsResultStatic(String nonNlsParam);
    
    native @Nls String nlsResult(String nonNlsParam);
}

@Target(ElementType.TYPE_USE)
@interface NlsSafe {
}
