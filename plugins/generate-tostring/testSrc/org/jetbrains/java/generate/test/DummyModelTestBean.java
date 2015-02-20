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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * This is a dummy test bean for testing the toString() plugin.
 */
public abstract class DummyModelTestBean {
    private String age;

    private static class MyInnerClass {
        private String title;

        public boolean isMyMethod() {
            return false;
        }

        public String toString() {
            return "MyInnerClass{" +
                    "title='" + title + '\'' +
                    '}';
        }
    }

    public String getName() {
        return "Claus";
    }

    public String getAge() {
        return age;
    }

    public boolean isOld() {
        return true;
    }

    public abstract String getConfiguration();

    public static String getCache() {
        return null;
    }

    public void nonGetterMethod() {
        List list = new ArrayList();
        Collections.sort(list, new Comparator() {
            public int compare(Object o, Object o1) {
                return 0;
            }
        });
    }

    public boolean isMyMethod() {
        age = "31";
        return true;
    }


    public String toString() {
        return "DummyModelTestBean{" +
                "age='" + age + '\'' +
                '}';
    }
}
