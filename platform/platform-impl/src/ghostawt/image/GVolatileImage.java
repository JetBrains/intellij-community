package ghostawt.image;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.VolatileImage;

public class GVolatileImage extends VolatileImage {
    private GraphicsConfiguration _graphicsConfiguration;
    private int _width;
    private ImageCapabilities _caps;
    private int _height;
    private int _transparency;

    public GVolatileImage(GraphicsConfiguration graphicsConfiguration, int width, int height, int transparency, ImageCapabilities caps) {
        _graphicsConfiguration = graphicsConfiguration;
        _width = width;
        _height = height;
        _transparency = transparency;
        _caps = caps;
    }

    @Override
    public BufferedImage getSnapshot() {
        return new BufferedImage(_width, _height, BufferedImage.TYPE_INT_RGB);
    }

    @Override
    public int getWidth() {
        return _width;
    }

    @Override
    public int getHeight() {
        return _height;
    }

    @Override
    public Graphics2D createGraphics() {
        //GraphicsEnvironment env =
        //        GraphicsEnvironment.getLocalGraphicsEnvironment();
        //return env.createGraphics(null);

        return new GGraphics2D(_graphicsConfiguration);
    }

    @Override
    public int validate(GraphicsConfiguration gc) {
        return 0;
    }

    @Override
    public boolean contentsLost() {
        return false;
    }

    @Override
    public ImageCapabilities getCapabilities() {
        return _caps;
    }

    @Override
    public int getWidth(ImageObserver observer) {
        return _width;
    }

    @Override
    public int getHeight(ImageObserver observer) {
        return _height;
    }

    @Override
    public Object getProperty(String name, ImageObserver observer) {
        return null;
    }

}
