package com.siyeh.igtest.bugs;

public class NonShortCircuitBooleanInspection
{
    private boolean m_bar = true;
    private boolean m_baz = false;

    public NonShortCircuitBooleanInspection()
    {
        final boolean nonShortAnd = m_bar & m_baz;
        final boolean nonShortOr = m_bar | m_baz;
        m_bar |= m_baz;
        m_bar &= m_baz;
    }

}
