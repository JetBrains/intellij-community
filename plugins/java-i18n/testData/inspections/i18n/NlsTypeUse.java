package org.jetbrains.annotations;

import java.lang.annotation.*;
import java.util.*;
import java.util.function.*;
import static java.lang.annotation.ElementType.*;

@Retention(RetentionPolicy.CLASS)
@Target({METHOD, FIELD, PARAMETER, LOCAL_VARIABLE, TYPE_USE, TYPE, PACKAGE})
@interface Nls {}

interface AnnotatedSam {
    @Nls String getString();
}

class NlsTypeUse {
    native void foo(AnnotatedSam str);
    native void foo2(Supplier<@Nls String> str);
    native void foo3(Supplier<String> str);
    
    Supplier<@Nls String> getSupplier() {
        return () -> <warning descr="Hardcoded string literal: \"foo\"">"foo"</warning>;
    }
    
    void test(Map<String, @Nls String> map, BiConsumer<@Nls String, String> cons) {
        foo(() -> <warning descr="Hardcoded string literal: \"bar\"">"bar"</warning>);
        foo2(() -> <warning descr="Hardcoded string literal: \"bar\"">"bar"</warning>);
        foo3(() -> "bar");

        map.put("foo", <warning descr="Hardcoded string literal: \"bar\"">"bar"</warning>);
        cons.accept(<warning descr="Hardcoded string literal: \"foo\"">"foo"</warning>, "bar");
    }
}