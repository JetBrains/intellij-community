/**
 * (c) 2008 Carp Technologies BV
 * Brouwerijstraat 1, 7523XC Enschede
 * Created: 20080513, 4:16:29 PM
 */
package com.siyeh.igtest.numeric.char_context;

/**
 * @author <A href="bas@carp-technologies.nl">Bas Leijdekkers</a>
 */
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
}