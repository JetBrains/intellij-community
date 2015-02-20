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

import java.util.logging.Logger;

/**
 * This is a dummy test bean for testing the toString() plugin.
 */
public class DummySortTestBean {

    private Logger myLogger;
    private int age;
    private int year;
    private String name;
    private String email;
    private boolean _member;
    private DummyTestBean bean;


    public String toString() {
        return "DummySortTestBean{" +
                "age=" + age +
                ", year=" + year +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", _member=" + _member +
                ", bean=" + bean +
                '}';
    }
}
