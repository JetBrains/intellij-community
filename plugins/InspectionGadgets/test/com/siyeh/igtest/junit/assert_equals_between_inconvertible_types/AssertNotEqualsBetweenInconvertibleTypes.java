package com.siyeh.igtest.junit.assert_equals_between_inconvertible_types;

import static org.junit.Assert.assertNotEquals;
import org.junit.Test;

import java.util.*;

public class AssertNotEqualsBetweenInconvertibleTypes {

    @Test
    public void test() {
        <warning descr="Assertion never fails. Redundant assertion: incompatible types are compared 'String' and 'int'">assertNotEquals</warning>("java", 1);
        <warning descr="Assertion never fails. Redundant assertion: incompatible types are compared 'int[]' and 'double'">assertNotEquals</warning>(new int[0], 1.0);
        assertNotEquals(new int[0], new int[1]); //ok
    }
}