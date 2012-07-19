package com.siyeh.igtest.abstraction.magic_number;

import java.util.Set;
import java.util.HashSet;

@Size(max = 15)
public class MagicNumber
{
    private static final int s_foo = 400;
    private int m_foo = 400;
    private static int s_foo2 = 400;
    private final int m_foo2 = 400;
    private static final Set s_set = new HashSet(400);

    public static void main(String[] args)
    {
        final Set set = new HashSet(400);
        set.toString();
    }

    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final MagicNumber magicNumber = (MagicNumber) o;

        if (m_foo != magicNumber.m_foo) return false;
        if (m_foo2 != magicNumber.m_foo2) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = m_foo;
        result = 29 * result + m_foo2;
        return result;
    }
}
@interface Size {
  int max();
}