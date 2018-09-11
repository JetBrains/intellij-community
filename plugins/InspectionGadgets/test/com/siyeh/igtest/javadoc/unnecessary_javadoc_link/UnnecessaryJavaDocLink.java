package com.siyeh.igtest.javadoc.unnecessary_javadoc_link;

public class UnnecessaryJavaDocLink {

    /**
     * {<warning descr="'@link' pointing to containing class is unnecessary">@link</warning> UnnecessaryJavaDocLink}
     * {<warning descr="'@linkplain' pointing to this method is unnecessary">@linkplain</warning> UnnecessaryJavaDocLink#equals(Object)}
     * <warning descr="'@see' pointing to super method is unnecessary">@see</warning> Object#equals(Object)
     */
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    /**
     * {<warning descr="'@link' pointing to this method is unnecessary">@link</warning> #foo()}
     * <warning descr="'@see' pointing to this method is unnecessary">@see</warning> #foo()
     */
    void foo() {

    }

    /**
     * @see com.siyeh.igtest.javadoc.unnecessary_javadoc_link.UnnecessaryJavaDocLink1 something
     */
    void bar() {}
}
