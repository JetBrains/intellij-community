/**
 * (c) 2008 Carp Technologies BV
 * Brouwerijstraat 1, 7523XC Enschede
 * Created: 20080901, 1:19:26 PM
 */
package com.siyeh.ig.imports;

import com.IGInspectionTestCase;

/**
 * @author <A href="bas@carp-technologies.nl">Bas Leijdekkers</a>
 */
public class UnusedImportInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/imports/unused",
                new UnusedImportInspection());
    }
}