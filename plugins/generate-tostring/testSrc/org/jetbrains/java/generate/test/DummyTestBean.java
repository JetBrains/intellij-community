/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.test;

import org.jetbrains.java.generate.config.Config;

import java.io.Serializable;
import java.util.Date;

/**
 * This is a dummy test bean for testing the toString() plugin.
 */
@SuppressWarnings("unused")
public class DummyTestBean extends Config implements Serializable {

    public static final String CONST_FIELD = "XXX_XXX";
    private static final String CONST_FIELD_PRIV = "XXX_XXX";
    @SuppressWarnings("UnnecessaryModifier")
    private static final transient String tran = "xxx";

//    private static String myStaticString;
//    private String[] nameStrings = new String[] { "Claus", "Ibsen" };
//    private String otherStrs[];
//    public int[] ipAdr = new int[] { 127, 92 };
//    private List arrList = new ArrayList();
//
    //    private Calendar cal = Calendar.getInstance();
    private final Date bday = new java.util.Date();

//
//    public String pubString;
//    private String firstName;
//    private java.sql.Date sqlBirthDay = new java.sql.Date(new java.util.Date().getTime());
//    private List children;
//    public Object someObject;
//    public Object[] moreObjects;
//    public Map cityMap;
//    public Set courses;
    //    private byte smallNumber;
    private float salary;
//    protected String procString;
//    String defaultPackageString;

//    private java.util.Date utilDateTime = new java.util.Date();

    private final DummyTestBean singleton = null;

    public DummyTestBean getSingleton() {
        return singleton;
    }


    public String toString() {
        return "DummyTestBean{" +
                "tran='" + tran + '\'' +
                ", bday=" + bday +
                ", salary=" + salary +
                ", singleton=" + singleton +
                '}';
    }
}
