class Foo {
    void foo(String... s) {
        foo("literal", "literal"); //   MYNON-NLS0
        String d = "xxxxx";  // NON-NLS
        String d0 = "xxxxx", d01="sssss";  //MYNON-NLS
        String d1 = "xxxxx";  // MYNON-NLS?

        String d2 = "xxxxx"; /* MYNON-NLS ??? */
        String wtf="MYNON-NLS";
        String dw2 = "xxxxx"; String wtw="MYNON-NLS"; /* MYNON-NLS ??? */
    }
}