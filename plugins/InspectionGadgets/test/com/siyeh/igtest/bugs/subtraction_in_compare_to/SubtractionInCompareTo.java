/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.igtest.bugs.subtraction_in_compare_to;

import java.util.Map;
import java.util.HashMap;

public class SubtractionInCompareTo implements java.util.Comparator<SubtractionInCompareTo>, Comparable<Object>
{
    private int m_foo = 3;

    public int compareTo(Object foo)
    {
        final int temp = m_foo + ((SubtractionInCompareTo)foo).m_foo;
        return <warning descr="Subtraction 'm_foo - ((SubtractionInCompareTo)foo).m_foo' in 'compareTo()' may result in overflow errors">m_foo - ((SubtractionInCompareTo)foo).m_foo</warning>;
    }


    @Override
    public int compare(SubtractionInCompareTo o1, SubtractionInCompareTo o2) {
        return <warning descr="Subtraction 'o1.m_foo - o2.m_foo' in 'compareTo()' may result in overflow errors">o1.m_foo - o2.m_foo</warning>;
    }

    {
        java.util.Comparator<Integer> c = (s1, s2) -> <warning descr="Subtraction 's1 - s2' in 'compareTo()' may result in overflow errors">s1 - s2</warning>;
    }
}
class A implements Comparable<A> {
    final String s = "";
    public int compareTo(A a) {
        return s.length() - a.s.length();
    }
}
class B implements Comparable<B> {
    final Map<String, String> map = new HashMap();
    public int compareTo(B b) {
        return map.size() - b.map.size();
    }
}
class C implements Comparable<C> {
    private short small = 1;
    public int compareTo(C c) {
        return small - c.small;
    }
}