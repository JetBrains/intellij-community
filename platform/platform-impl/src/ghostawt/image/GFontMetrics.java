// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt.image;

import sun.font.FontDesignMetrics;

import java.awt.*;

public class GFontMetrics extends FontMetrics {
    private static final long serialVersionUID = 8973610274117460237L;

    private FontDesignMetrics fdm;

    protected GFontMetrics(Font font) {
        super(font);
        fdm = FontDesignMetrics.getMetrics(font);
    }
    
    @Override
    public int stringWidth(String str) {
        if(str == null) return 0;
        return str.length() * font.getSize();
    }

    @Override
    public int charWidth(char ch) {
        return fdm.charWidth(ch);
    }
}
