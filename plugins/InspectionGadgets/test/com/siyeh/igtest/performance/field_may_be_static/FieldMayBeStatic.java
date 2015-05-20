package com.siyeh.igtest.performance.field_may_be_static;

public class FieldMayBeStatic
{
    private final int <warning descr="Field 'm_fooBar' may be 'static'">m_fooBar</warning> = 3;
    private final int m_fooBaz = m_fooBar;

    {
        System.out.println("m_fooBaz = " + m_fooBaz);
    }

    private static class Namer {
        private String name = "name";

        public String getString() {
            return name;
        }

        public void run() {

            new Namer() {
                final String <warning descr="Field 'constant' may be 'static'">constant</warning> = "";
                final String usage = "Usage: " + getString();
                public void action() {

                    System.out.println(usage);
                }
            };
        }
    }
}