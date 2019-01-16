package ghostawt;

import java.awt.font.TextAttribute;
import java.awt.im.InputMethodHighlight;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GInputMethod {
    private static Map<TextAttribute, Integer> highlightStyle;

    static {
        highlightStyle = new HashMap<TextAttribute, Integer>(1);
        highlightStyle.put(TextAttribute.INPUT_METHOD_UNDERLINE, TextAttribute.UNDERLINE_LOW_DOTTED);
        highlightStyle = Collections.unmodifiableMap(highlightStyle);
    }

    public static Map<TextAttribute, Integer> mapInputMethodHighlight(InputMethodHighlight highlight) {
        return highlightStyle;
    }
}