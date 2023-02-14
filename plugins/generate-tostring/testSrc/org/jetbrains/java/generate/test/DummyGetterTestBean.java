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

/**
 * This is a dummy test bean for testing the toString() plugin.
 */
@SuppressWarnings("unused")
public class DummyGetterTestBean {

    public int getX() throws RuntimeException { return 0; }
    public DummyModelTestBean getModel() { return null; }
    public DummyModelTestBean getDummyModel() { return null; }
    private DummyModelTestBean model;

    /**
     * Hello Claus this is DummyGetterTestBean
     */
    public String toString() {
        return "DummyGetterTestBean{" +
                "model=" + model +
                ", x=" + getX() +
                ", dummyModel=" + getDummyModel() +
                '}';
    }
}
