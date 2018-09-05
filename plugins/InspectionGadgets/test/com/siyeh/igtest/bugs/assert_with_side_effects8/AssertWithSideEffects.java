package com.siyeh.igtest.bugs.assert_with_side_effects;
import java.util.regex.*;
public class AssertWithSideEffects {
    void assertMutation() {
        Matcher m = Pattern.compile("foobar").matcher("foo");
        assert m.matches();

        assert Pattern.compile("foobar").matcher("foo").matches();
    }
}