package com.siyeh.igtest.naming.upper_case_field_name_not_constant;

public class UpperCaseFieldNameNotConstant {
    public int <warning descr="Non-constant field 'FOO' with constant-style name">F<caret>OO</warning> = 3;
    public static int <warning descr="Non-constant field 'FOO2' with constant-style name">FOO2</warning> = 3;
    public final int <warning descr="Non-constant field 'FOO3' with constant-style name">FOO3</warning> = 3;
    public static final int FOO4 = 3;
}
