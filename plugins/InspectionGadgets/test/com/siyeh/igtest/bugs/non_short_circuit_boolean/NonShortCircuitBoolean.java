package com.siyeh.igtest.bugs.non_short_circuit_boolean;

public class NonShortCircuitBoolean
{
    private boolean m_bar = true;
    private boolean m_baz = false;
    private boolean m_foo = true;

    public NonShortCircuitBoolean()
    {
        final boolean nonShortAnd = m_bar & m_baz & m_foo;
        final boolean nonShortOr = m_bar | m_baz;
        m_bar |= m_baz;
        m_bar &= m_baz;
    }

}
