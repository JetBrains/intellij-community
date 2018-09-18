// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.trivialif;

import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class MergeParallelIfsIntentionTest extends IPPTestCase {

  public void testCommentsAreKept() {
    doTest("class C {\n" +
           "    void m(boolean b) {\n" +
           "        if/*_Merge 'if's*/ (b) //simple end comment\n" +
           "                {\n" +
           "                        System.out.println(2); //1\n" +
           "    }\n" +
           "        if (b) {//2\n" +
           "            System.out.println(/*3*/1);//4\n" +
           "        }//5\n" +
           "    }\n" +
           "}",

           "class C {\n" +
           "    void m(boolean b) {\n" +
           "        //simple end comment\n" +
           "        if (b) {\n" +
           "            System.out.println(2); //1\n" +
           "            //2\n" +
           "            System.out.println(/*3*/1);//4\n" +
           "        }\n" +
           "    }\n" +
           "}");
  }
}
