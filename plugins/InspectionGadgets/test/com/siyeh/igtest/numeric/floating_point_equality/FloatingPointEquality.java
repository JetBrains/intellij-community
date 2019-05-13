
public class FloatingPointEquality
{
    private double m_bar;
    private double m_baz;
    private float m_barf;
    private float m_bazf;

    public static final float TENTH = 0.1f;
    public static final float fifth = 0.2f;

    public FloatingPointEquality()
    {
        m_bar = 0.0;
        m_baz = 1.0;
        m_barf = TENTH;
        m_bazf = fifth;
    }

    public void foo()
    {
        if (<warning descr="'m_bar == m_baz': floating point values compared for exact equality">m_bar == m_baz</warning>) {
            System.out.println("m_bar = " + m_bar);
        }
        if (<warning descr="'m_barf == m_bazf': floating point values compared for exact equality">m_barf == m_bazf</warning>) {
            System.out.println("m_barf = " + m_barf);
        }
        if (<warning descr="'m_barf != m_bar': floating point values compared for exact equality">m_barf != m_bar</warning>) {
            System.out.println("m_barf = " + m_barf);
        }

        boolean infinity = m_bar == Double.POSITIVE_INFINITY;
        boolean zero = m_barf == 0.0f;
        boolean zero2 = m_barf == 0;
        boolean zero3 = m_barf == 0L;

    }


}
