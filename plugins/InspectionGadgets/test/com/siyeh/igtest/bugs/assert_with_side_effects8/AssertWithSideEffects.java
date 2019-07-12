package com.siyeh.igtest.bugs.assert_with_side_effects;
import java.util.regex.*;
import java.util.*;
public class AssertWithSideEffects {
    void assertMutation() {
        Matcher m = Pattern.compile("foobar").matcher("foo");
        <warning descr="'assert' has side effects: call to 'matches()' mutates 'm'">assert</warning> m.matches();

        assert Pattern.compile("foobar").matcher("foo").matches();
    }

    void assertMutation(Set<String> set) {
        <warning descr="'assert' has side effects: call to 'add()' mutates 'set'">assert</warning> set.add("foo");

        assert new HashSet<>().add("bar");

        assert (set.isEmpty() ? new TreeSet<>() : new HashSet<>()).add("baz");
    }
}