/**
 * (c) 2006 Carp Technologies BV
 * Brouwerijstraat 1, 7523XC Enschede
 * Created: 20060329, 4:52:49 PM
 */
package com.siyeh.igtest.abstraction;

/**
 * @author <A href="bas@carp-technologies.nl">Bas Leijdekkers</a>
 */
public class MethodOnlyUsedFromInnerClass {

    private class Inner {
        void foo() {
            add();
            add();
        }
    }


    private void add() {}
}