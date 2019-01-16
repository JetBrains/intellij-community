// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt.image;

import java.awt.*;
import java.util.Locale;

public class GFontManager {
    private static GFontManager instance;

    public static GFontManager getInstance() {
        if (instance == null) {
            instance = new GFontManager();
        }
        return instance;
    }

    private Font systemFont;

    private GFontManager() {
    }

    public Font[] getAllInstalledFonts() {
        if (systemFont == null) {
            loadSystemFont();
        }
        return new Font[] { systemFont };
    }

    private void loadSystemFont() {
        systemFont = new Font("System", Font.PLAIN, 12);
    }

    public String[] getInstalledFontFamilyNames(Locale requestedLocale) {
        return new String[] { "System" };
    }
}
