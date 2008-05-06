/**
 * (c) 2008 Carp Technologies BV
 * Brouwerijstraat 1, 7523XC Enschede
 * Created: 14-feb-2008
 */
package com.siyeh.igtest.style.unnecessary_valueof;

/**
 * @author <A href="bas@carp-technologies.nl">Bas Leijdekkers</a>
 */
public class UnnecessaryCallToStringValueOf {

    String foo() {
        return "star" + String.valueOf(7);
    }

    String bar() {
        char[] cs = {'!'};
        return "wars" + String.valueOf(cs);
    }

}