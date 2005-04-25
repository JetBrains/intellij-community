package com.siyeh.igtest.portability;

import java.awt.peer.ComponentPeer;
import java.awt.*;
import java.awt.image.ColorModel;
import java.awt.image.VolatileImage;
import java.awt.image.ImageProducer;
import java.awt.image.ImageObserver;
import java.awt.event.PaintEvent;


public class UseOfAWTPeerClassInspection implements ComponentPeer{
    UseOfAWTPeerClassInspection foo = new UseOfAWTPeerClassInspection();

    public void destroyBuffers() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void disable() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void dispose() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void enable() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void hide() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void show() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void updateCursorImmediately() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean canDetermineObscurity() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean handlesWheelScrolling() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isFocusable() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isObscured() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void reshape(int i, int i1, int i2, int i3) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setBounds(int i, int i1, int i2, int i3) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void repaint(long l, int i, int i1, int i2, int i3) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setEnabled(boolean b) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setVisible(boolean b) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void handleEvent(AWTEvent awtEvent) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void createBuffers(int i, BufferCapabilities bufferCapabilities) throws AWTException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void flip(BufferCapabilities.FlipContents flipContents) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setBackground(Color color) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setForeground(Color color) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean requestFocus(Component component, boolean b, boolean b1, long l) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Dimension getMinimumSize() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Dimension getPreferredSize() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Dimension minimumSize() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Dimension preferredSize() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setFont(Font font) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public Graphics getGraphics() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void paint(Graphics graphics) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void print(Graphics graphics) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public GraphicsConfiguration getGraphicsConfiguration() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Image getBackBuffer() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Image createImage(int i, int i1) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Point getLocationOnScreen() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Toolkit getToolkit() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void coalescePaintEvent(PaintEvent paintEvent) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public ColorModel getColorModel() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public VolatileImage createVolatileImage(int i, int i1) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public FontMetrics getFontMetrics(Font font) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Image createImage(ImageProducer imageProducer) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public int checkImage(Image image, int i, int i1, ImageObserver imageObserver) {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean prepareImage(Image image, int i, int i1, ImageObserver imageObserver) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
