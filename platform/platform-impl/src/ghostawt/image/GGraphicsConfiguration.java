// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt.image;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.ColorModel;
import java.awt.image.VolatileImage;

public class GGraphicsConfiguration extends GraphicsConfiguration {
    private GGraphicsDevice device;

//    public BufferedImage buffer;

    public GGraphicsConfiguration(GGraphicsDevice device) {
        this.device = device;
    }

    @Override
    public GraphicsDevice getDevice() {
        return device;
    }

    @Override
    public ColorModel getColorModel() {
        return device.getColorModel();
    }

    @Override
    public ColorModel getColorModel(int transparency) {
        return getColorModel();
    }

    @Override
    public AffineTransform getDefaultTransform() {
        return new AffineTransform();
    }

    @Override
    public AffineTransform getNormalizingTransform() {
        GhostGraphicsEnvironment ge = (GhostGraphicsEnvironment) GraphicsEnvironment.getLocalGraphicsEnvironment();
        double xscale = ge.getXResolution() / 72.0;
        double yscale = ge.getYResolution() / 72.0;
        return new AffineTransform(xscale, 0.0, 0.0, yscale, 0.0, 0.0);
    }

    @Override
    public Rectangle getBounds() {
        GhostGraphicsEnvironment ge = (GhostGraphicsEnvironment) GraphicsEnvironment.getLocalGraphicsEnvironment();
        return new Rectangle(1024, 768);
    }

    public VolatileImage createCompatibleVolatileImage(int width, int height, ImageCapabilities caps, int transparency) throws AWTException {
        return new GVolatileImage(this, width, height, transparency, caps);

       //return new BufferedImage(width, height,BufferedImage.TYPE_INT_ARGB);

    }

}
