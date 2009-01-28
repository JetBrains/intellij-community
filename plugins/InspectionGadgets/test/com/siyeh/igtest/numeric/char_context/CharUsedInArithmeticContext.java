package com.siyeh.igtest.numeric.char_context;

public class CharUsedInArithmeticContext {

    String foo() {
        return 'a' + "asdfsad";
    }

    void bar() {
        int i = 'a' + 5;
    }

    void airplane(int i1, int i2) {
        System.out.println (i2 + '-' + i1 + " = " + (i2 - i1));
    }

    boolean compare(char c1, char c2) {
        return c1 != c2;
    }

    public static void checkDigit(int ch) {
		if (ch < '0' || ch > '9') {
			throw new RuntimeException("unexpected: " + ch);
		}
	}
}
