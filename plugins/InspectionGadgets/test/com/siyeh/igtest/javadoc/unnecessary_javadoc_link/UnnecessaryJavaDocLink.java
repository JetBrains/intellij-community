package com.siyeh.igtest.javadoc.unnecessary_javadoc_link;

public class UnnecessaryJavaDocLink {

    /**
     * {@link UnnecessaryJavaDocLink}
     * {@linkplain UnnecessaryJavaDocLink#equals(Object)}
     * @see Object#equals(Object)
     */
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    /**
     * {@link #foo()}
     * @see #foo()
     */
    void foo() {

    }

    /**
     * @see com.siyeh.igtest.javadoc.unnecessary_javadoc_link.UnnecessaryJavaDocLink1 something
     */
    void bar() {}
}
