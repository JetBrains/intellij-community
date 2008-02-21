package com.siyeh.igtest.confusing;

public class ConfusingOctalEscapeInspection {
    public static final String foo =  "asdf\01234";
    public static final String boo =  "asdf\01834";

    public static String escapeLdapSearchValue(String value) {
        // see RFC 2254
        String escapedStr = value;
        escapedStr = escapedStr.replaceAll("\\\\", "\\\\5c");
        escapedStr = escapedStr.replaceAll("\\*", "\\\\2a");
        escapedStr = escapedStr.replaceAll("\\(", "\\\\28");
        escapedStr = escapedStr.replaceAll("\\)", "\\\\29");
        return escapedStr;
    }
}
