class Foo {
    void foo(String... s) {
        foo("literal", "literal"); //   MYNON-NLS0
        String d = <warning descr="Hard coded string literal: \"xxxxx\"">"xxxxx"</warning>;  // NON-NLS
        String d0 = "xxxxx", d01="sssss";  //MYNON-NLS
        String d1 = "xxxxx";  // MYNON-NLS?

        String d2 = "xxxxx"; /* MYNON-NLS ??? */
        String wtf=<warning descr="Hard coded string literal: \"MYNON-NLS\"">"MYNON-NLS"</warning>;
        String dw2 = "xxxxx"; String wtw="MYNON-NLS"; /* MYNON-NLS ??? */
    }
}