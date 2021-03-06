package com.siyeh.igtest.migration.string_buffer_replaceable_by_string_builder;

import java.lang.annotation.*;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringBufferReplaceableByStringBuilder {

    public void foo()
    {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("bar");
        buffer.append("bar");
        System.out.println(buffer.toString());
    }

    public StringBuffer foo2()
    {
        final StringBuffer buffer = new StringBuffer();
        buffer.append("bar");
        buffer.append("bar");
        System.out.println(buffer.toString());
        return buffer;
    }

    StringBuffer bar() {
        final StringBuffer stringBuffer = new StringBuffer();
        return stringBuffer.append("asdf");
    }

    String x() {
      StringBuffer buffer;
      buffer = foo2();
      buffer.append("x");
      return buffer.toString();
    }

    void argument(StringBuffer buffer) {}
    void caller() {
        final StringBuffer sb = new StringBuffer();
        argument(sb.append("asdf").append("wait"));
    }

    void lambda(StringBuilder logContainer) {
        StringBuffer allLogs = new StringBuffer();
        Runnable r = () -> {
            allLogs.append("logs"); // might be used in another thread
        };
        logContainer.append(allLogs);
    }

  private final Random rnd = new Random(123);

  void convert() {
    @NonNls StringBuilder sb = new StringBuilder();
    for (int i = 0, n = rnd.nextInt(15) + 1; i < n; i++)
      sb.append(nextChar());
    String symbol = new String(sb);
    System.out.println(symbol);
  }

  private char nextChar() {
    int i = rnd.nextInt('Z' - 'A');
    char c = (char) ('A' + i);
    return c;
  }

  private String doSome() {
    StringBuilder sb;
    String str = "bla";
    (sb) = (new StringBuilder());
    sb.append(str);
    return sb.toString();
  }

  private String chained() {
    StringBuilder b = null;
    b = new StringBuilder().append("one").insert(0, "two").appendCodePoint(17);
    return b.toString();
  }

  Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\w[-\\w]*)\\}");

  String substitute(Map<String, String> lookup, String s) {
    StringBuilder sb = new StringBuilder();
    Matcher m = PLACEHOLDER_PATTERN.matcher(s);
    while (m.find()) {
      String name = m.group(1);
      String replacement = lookup.get(name);
      assert replacement != null;
      m.appendReplacement(sb, replacement);
    }
    m.appendTail(sb).append("end");
    return m.appendTail(sb).toString();
  }

  void x(Matcher matcher) {
    StringBuffer sb2 = new StringBuffer();
    StringBuffer sb1 = switch(0) {default -> matcher.appendTail(sb2);};
  }
}
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE, ElementType.TYPE, ElementType.PACKAGE})
@interface NonNls {}
