package org.hanuna.gitalk.common;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static junit.framework.Assert.assertEquals;

/**
 * @author erokhins
 */
public class HashInvertibleMapTest {

    private String setToStr(Set<Integer> set) {
        if (set.isEmpty()) {
            return "";
        }
        List<Integer> list = new ArrayList<Integer>(set);
        Collections.sort(list);
        StringBuilder s = new StringBuilder();
        s.append(list.get(0));
        for (int i = 1; i < list.size(); i++) {
            s.append(" ").append(list.get(i));
        }
        return s.toString();
    }

    @Test
    public void simpleAdd() {
        InvertibleMap<Integer, String> map = new HashInvertibleMap<Integer, String>();
        map.put(1, "one");
        assertEquals("one", map.get(1));
        assertEquals("1", setToStr(map.getKeys("one")));
    }

    @Test
    public void notExistedElement() {
        InvertibleMap<Integer, String> map = new HashInvertibleMap<Integer, String>();
        assertEquals(null, map.get(0));
        assertEquals("", setToStr(map.getKeys("str")));
    }

    @Test
    public void simpleRemove() {
        InvertibleMap<Integer, String> map = new HashInvertibleMap<Integer, String>();
        map.put(1, "one");
        map.remove(1);

        assertEquals(null, map.get(1));
        assertEquals("", setToStr(map.getKeys("one")));
    }


    @Test
    public void oneValue() {
        InvertibleMap<Integer, String> map = new HashInvertibleMap<Integer, String>();
        map.put(1, "one");
        map.put(2, "one");
        assertEquals("one", map.get(1));
        assertEquals("one", map.get(2));
        assertEquals("1 2", setToStr(map.getKeys("one")));

        map.put(3, "one");
        assertEquals("one", map.get(1));
        assertEquals("one", map.get(2));
        assertEquals("one", map.get(3));
        assertEquals("1 2 3", setToStr(map.getKeys("one")));
    }

    @Test
    public void oneValueRemove() {
        InvertibleMap<Integer, String> map = new HashInvertibleMap<Integer, String>();
        map.put(1, "str");
        map.put(2, "str");
        map.put(3, "str");

        map.remove(2);
        assertEquals("str", map.get(1));
        assertEquals(null, map.get(2));
        assertEquals("str", map.get(3));
        assertEquals("1 3", setToStr(map.getKeys("str")));
    }

    @Test
    public void moreValuesAndKeys() {
        InvertibleMap<Integer, String> map = new HashInvertibleMap<Integer, String>();
        map.put(1, "one");
        map.put(2, "str");
        map.put(3, "str");
        map.put(9, "one");
        map.put(0, "zero");

        assertEquals("one", map.get(1));
        assertEquals("str", map.get(2));
        assertEquals("str", map.get(3));
        assertEquals("one", map.get(9));
        assertEquals("zero", map.get(0));

        assertEquals("1 9", setToStr(map.getKeys("one")));
        assertEquals("0", setToStr(map.getKeys("zero")));
        assertEquals("2 3", setToStr(map.getKeys("str")));


    }

}
