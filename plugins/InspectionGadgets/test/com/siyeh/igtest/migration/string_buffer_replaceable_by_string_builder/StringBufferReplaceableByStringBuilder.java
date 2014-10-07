package com.siyeh.igtest.migration.string_buffer_replaceable_by_string_builder;

public class StringBufferReplaceableByStringBuilder {

    public void foo()
    {
        final StringBuffer <warning descr="'StringBuffer buffer' may be declared as 'StringBuilder'">buffer</warning> = new StringBuffer();
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

    void argument(StringBuffer buffer) {}
    void caller() {
        final StringBuffer sb = new StringBuffer();
        argument(sb.append("asdf").append("wait"));
    }
}
