/**
 * (c) 2006 Carp Technologies BV
 * Brouwerijstraat 1, 7523XC Enschede
 * Created: 20060329, 4:53:57 PM
 */
package com.siyeh.igtest.abstraction;

/**
 * @author <A href="bas@carp-technologies.nl">Bas Leijdekkers</a>
 */
public class StaticMethodOnlyUsedInOneClass {
    public static void method() {

    }

}
class OneClass {
    static {
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
    }
}