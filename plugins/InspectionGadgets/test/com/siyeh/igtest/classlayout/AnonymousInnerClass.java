/**
 * (c) 2006 Carp Technologies BV
 * Brouwerijstraat 1, 7523XC Enschede
 * Created: 20070122, 2:32:01 PM
 */
package com.siyeh.igtest.classlayout;

/**
 * @author <A href="bas@carp-technologies.nl">Bas Leijdekkers</a>
 */
public class AnonymousInnerClass {

    enum Operation {
        PLUS   { double eval(double x, double y) { return x + y; } },
        MINUS  { double eval(double x, double y) { return x - y; } },
        TIMES  { double eval(double x, double y) { return x * y; } },
        DIVIDE { double eval(double x, double y) { return x / y; } };

        // Do arithmetic op represented by this constant
        abstract double eval(double x, double y);
    }
}