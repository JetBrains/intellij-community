package com.siyeh.igtest.internationalization;

import java.util.*;
import java.util.stream.*;
import org.jetbrains.annotations.NonNls;

public class StringToUpperWithoutLocale {
    public void foo()
    {
        final String foo = "foo".<warning descr="'String.toUpperCase()' called without specifying a Locale using internationalized strings">toUpperCase</warning>();
        final String bar = "bar".toUpperCase(Locale.US);
    }

    public void methodRef(List<String> list) {
      List<String> res = list.stream().map(String::<warning descr="'String.toUpperCase()' called without specifying a Locale using internationalized strings">toUpperCase</warning>).collect(Collectors.toList());
    }

    public void suppress(List<String> list) {
      @NonNls
      final String foo = "foo".toUpperCase();

      final String bar = foo.toLowerCase(); // foo is @NonNls: this is enough

      @NonNls
      List<String> res = list.stream().map(String::toUpperCase).collect(Collectors.toList());

      @NonNls
      List<String> res2 = list.stream().map(s -> s.toUpperCase()).collect(Collectors.toList());

    }
}
