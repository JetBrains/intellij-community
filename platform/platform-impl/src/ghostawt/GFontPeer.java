package ghostawt;

import sun.awt.PlatformFont;

public class GFontPeer extends PlatformFont {
    public GFontPeer(String name, int style) {
        super(name, style);
    }

    protected char getMissingGlyphCharacter() {
        return '\u274F';
    }
}
