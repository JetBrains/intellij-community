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
    List<@Nls String> NlsConst = Arrays.asList(<warning descr="Hardcoded string literal: \"foo\"">"foo"</warning>, <warning descr="Hardcoded string literal: \"bar\"">"bar"</warning>);
    List<String> NonNlsConst = Arrays.asList("foo", "bar");
    
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

    void typeArgsCustomType(NlsValueMap<String> map, NlsValueMap2<String, String> map2) {
        map.put("foo", <warning descr="Hardcoded string literal: \"bar\"">"bar"</warning>);
        map2.put("foo", <warning descr="Hardcoded string literal: \"bar\"">"bar"</warning>);
    }

    interface NlsValueMap<K> extends Map<K, @Nls String> {}

    interface NlsValueMap2<K, V> extends Map<K, @Nls V> {}
}