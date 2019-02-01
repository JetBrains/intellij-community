// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt.image;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Locale;

public class GhostGraphicsEnvironment extends GraphicsEnvironment {
    private GraphicsDevice screenDevice;

    @Override
    public GraphicsDevice[] getScreenDevices() throws HeadlessException {
        return new GraphicsDevice[]{getDefaultScreenDevice()};
    }

    @Override
    public GraphicsDevice getDefaultScreenDevice() throws HeadlessException {
        if (screenDevice == null) {
            screenDevice = new GGraphicsDevice();
        }
        return screenDevice;
    }

    //private GGraphics2D graphics2d;

    @Override
    public Graphics2D createGraphics(BufferedImage img) {
        return new GGraphics2D(getDefaultScreenDevice().getConfigurations()[0]);

        // GraphicsConfiguration configuration =getDefaultScreenDevice().getConfigurations()[0];
        //  ((GGraphicsConfiguration)configuration).buffer = img;
        //  return new SunGraphics2D(BufImgSurfaceData.createData(img), Color.white, Color.white,  Font.getFont("Times"));

        //if (graphics2d == null) {
        //    graphics2d = new GGraphics2D(getDefaultScreenDevice().getConfigurations()[0]);
        //}
        //
        //return graphics2d;
    }

    @Override
    public Font[] getAllFonts() {
        GFontManager fm = GFontManager.getInstance();
        Font[] installedFonts = fm.getAllInstalledFonts();
        return installedFonts;
    }

    @Override
    public String[] getAvailableFontFamilyNames() {
        return getAvailableFontFamilyNames(Locale.getDefault());
    }

    @Override
    public String[] getAvailableFontFamilyNames(Locale requestedLocale) {
        return GFontManager.getInstance().getInstalledFontFamilyNames(requestedLocale);
    }

    public double getXResolution() {
        return 96;
    }

    public double getYResolution() {
        return 96;
    }

}
