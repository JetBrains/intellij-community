/**
 * Copyright (c) 2018, http://www.snakeyaml.org
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.snakeyaml.engine.v1.common;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class CharConstants {
    private final static String ALPHA_S = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-_";

    private final static String LINEBR_S = "\n\r";
    private final static String NULL_OR_LINEBR_S = "\0" + LINEBR_S;
    private final static String NULL_BL_LINEBR_S = " " + NULL_OR_LINEBR_S;
    private final static String NULL_BL_T_LINEBR_S = "\t" + NULL_BL_LINEBR_S;
    private final static String NULL_BL_T_S = "\0 \t";
    private final static String URI_CHARS_S = ALPHA_S + "-;/?:@&=+$,_.!~*\'()[]%";

    public final static CharConstants LINEBR = new CharConstants(LINEBR_S);
    public final static CharConstants NULL_OR_LINEBR = new CharConstants(NULL_OR_LINEBR_S);
    public final static CharConstants NULL_BL_LINEBR = new CharConstants(NULL_BL_LINEBR_S);
    public final static CharConstants NULL_BL_T_LINEBR = new CharConstants(NULL_BL_T_LINEBR_S);
    public final static CharConstants NULL_BL_T = new CharConstants(NULL_BL_T_S);
    public final static CharConstants URI_CHARS = new CharConstants(URI_CHARS_S);

    public final static CharConstants ALPHA = new CharConstants(ALPHA_S);

    public final static Pattern ANCHOR_FORMAT = Pattern.compile("^[-_\\w]*$");

    private final static int ASCII_SIZE = 128;
    boolean[] contains = new boolean[ASCII_SIZE];

    private CharConstants(String content) {
        Arrays.fill(contains, false);
        for (int i = 0; i < content.length(); i++) {
            int c = content.codePointAt(i);
            contains[c] = true;
        }
    }

    public boolean has(int c) {
        return (c < ASCII_SIZE) ? contains[c] : false;
    }

    public boolean hasNo(int c) {
        return !has(c);
    }

    public boolean has(int c, String additional) {
        return has(c) || additional.indexOf(c, 0) != -1;
    }

    public boolean hasNo(int c, String additional) {
        return !has(c, additional);
    }

    /**
     * A mapping from an escaped character in the input stream to the character
     * that they should be replaced with.
     * <p>
     * YAML defines several common and a few uncommon escape sequences.
     */
    public final static Map<Integer, Character> ESCAPE_REPLACEMENTS = new HashMap();

    /**
     * A mapping from a character to be escaped to its code in the output stream. (used for emitting)
     * It contains the same as ESCAPE_REPLACEMENTS except ' ' and '/'
     * <p>
     * YAML defines several common and a few uncommon escape sequences.
     *
     * @see <a href="http://www.yaml.org/spec/1.2/spec.html#id2776092">5.7. Escaped Characters</a>
     */
    public final static Map<Character, Integer> ESCAPES = new HashMap();

    /**
     * A mapping from a character to a number of bytes to read-ahead for that
     * escape sequence. These escape sequences are used to handle unicode
     * escaping in the following formats, where H is a hexadecimal character:
     * <pre>
     * &#92;xHH         : escaped 8-bit Unicode character
     * &#92;uHHHH       : escaped 16-bit Unicode character
     * &#92;UHHHHHHHH   : escaped 32-bit Unicode character
     * </pre>
     */
    public final static Map<Character, Integer> ESCAPE_CODES = new HashMap();

    static {
        ESCAPE_REPLACEMENTS.put(Integer.valueOf('0'), '\0');// ASCII null
        ESCAPE_REPLACEMENTS.put(Integer.valueOf('a'), '\u0007');// ASCII bell
        ESCAPE_REPLACEMENTS.put(Integer.valueOf('b'), '\u0008'); // ASCII backspace
        ESCAPE_REPLACEMENTS.put(Integer.valueOf('t'), '\u0009'); // ASCII horizontal tab
        ESCAPE_REPLACEMENTS.put(Integer.valueOf('n'), '\n');// ASCII newline (line feed; &#92;n maps to 0x0A)
        ESCAPE_REPLACEMENTS.put(Integer.valueOf('v'), '\u000B');// ASCII vertical tab
        ESCAPE_REPLACEMENTS.put(Integer.valueOf('f'), '\u000C');// ASCII form-feed
        ESCAPE_REPLACEMENTS.put(Integer.valueOf('r'), '\r');// carriage-return (&#92;r maps to 0x0D)
        ESCAPE_REPLACEMENTS.put(Integer.valueOf('e'), '\u001B');// ASCII escape character (Esc)
        ESCAPE_REPLACEMENTS.put(Integer.valueOf(' '), '\u0020');// ASCII space
        ESCAPE_REPLACEMENTS.put(Integer.valueOf('"'), '\"');// ASCII double-quote
        ESCAPE_REPLACEMENTS.put(Integer.valueOf('/'), '/');// ASCII slash (#x2F), for JSON compatibility.
        ESCAPE_REPLACEMENTS.put(Integer.valueOf('\\'), '\\');// ASCII backslash
        ESCAPE_REPLACEMENTS.put(Integer.valueOf('N'), '\u0085');// Unicode next line
        ESCAPE_REPLACEMENTS.put(Integer.valueOf('_'), '\u00A0');// Unicode non-breaking-space
        ESCAPE_REPLACEMENTS.put(Integer.valueOf('L'), '\u2028');// Unicode line-separator
        ESCAPE_REPLACEMENTS.put(Integer.valueOf('P'), '\u2029');// Unicode paragraph separator

        ESCAPE_REPLACEMENTS.entrySet().stream()
                .filter(entry -> entry.getKey() != ' ' && entry.getKey() != '/')
                .forEach(entry -> ESCAPES.put(entry.getValue(), entry.getKey()));

        ESCAPE_CODES.put(Character.valueOf('x'), 2);// 8-bit Unicode
        ESCAPE_CODES.put(Character.valueOf('u'), 4);// 16-bit Unicode
        ESCAPE_CODES.put(Character.valueOf('U'), 8);// 32-bit Unicode (Supplementary characters are supported)
    }

}
