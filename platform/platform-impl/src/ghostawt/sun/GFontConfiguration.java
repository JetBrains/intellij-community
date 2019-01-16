// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt.sun;

import sun.awt.FontConfiguration;
import sun.font.SunFontManager;

import java.nio.charset.Charset;
import java.util.HashMap;

public class GFontConfiguration extends FontConfiguration {
    public GFontConfiguration(SunFontManager fm) {
        super(fm);
    }

    public GFontConfiguration(SunFontManager fm, boolean preferLocaleFonts, boolean preferPropFonts) {
        super(fm, preferLocaleFonts, preferPropFonts);
    }

    @Override
    protected Charset getDefaultFontCharset(String fontName) {
        return Charset.forName("UTF-8");
    }

    @Override
    protected String getEncoding(String awtFontName, String characterSubsetname) {
        return "default";
    }

    @Override
    protected String getFaceNameFromComponentFontName(String arg0) {
        return null;
    }

    @Override
    public String getFallbackFamilyName(String fontName, String defaultFallback) {
        return defaultFallback;
    }

    @Override
    protected String getFileNameFromComponentFontName(String componentFontName) {
        return getFileNameFromPlatformName(componentFontName);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void initReorderMap() {
        reorderMap = new HashMap<String, String>();
    }
}
