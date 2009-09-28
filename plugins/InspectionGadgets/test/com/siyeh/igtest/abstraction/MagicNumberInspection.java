package com.siyeh.igtest.abstraction;

import java.util.Set;
import java.util.HashSet;

public class MagicNumberInspection
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

        final MagicNumberInspection magicNumberInspection = (MagicNumberInspection) o;

        if (m_foo != magicNumberInspection.m_foo) return false;
        if (m_foo2 != magicNumberInspection.m_foo2) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = m_foo;
        result = 29 * result + m_foo2;
        return result;
    }
}