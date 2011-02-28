/**
 * (c) 2008 Carp Technologies BV
 * Brouwerijstraat 1, 7523XC Enschede
 * Created: 20081028, 1:14:34 PM
 */
package com.siyeh.ig.naming;

import com.siyeh.ig.IGInspectionTestCase;

/**
 * @author <A href="bas@carp-technologies.nl">Bas Leijdekkers</a>
 */
public class StandardVariableNamesInspectionTest
        extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/naming/standard_variable_names",
                new StandardVariableNamesInspection());
    }
}