
package com.siyeh.igtest.style.unnecessary_semicolon;;
import java.util.List;;
public class UnnecessarySemicolonInspection
{
    int i;
    ; // comment
    public UnnecessarySemicolonInspection()
    {
        ; // this is a comment
        ; /* another */
    }
}  ; // comment
enum Status
{
    BUSY    ("BUSY"    ),
    DONE    ("DONE"    )  ; // <<---- THIS IS NOT UNNECESSARY

    public final String text;   ;
    private Status (String i_text) {
        text = i_text;
    }
}
enum BuildType
{
    ;

    public String toString() {
        return super.toString();
    }
}
class C {
    void m() throws Exception {
        try (AutoCloseable r1 = null; AutoCloseable r2 = null /*ok*/ ) { }
        try (AutoCloseable r1 = null; AutoCloseable r2 = null; /*warn*/ ) { }
    }
}