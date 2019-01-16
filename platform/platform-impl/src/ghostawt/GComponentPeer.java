// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import sun.awt.CausedFocusEvent.Cause;
import sun.awt.image.SunVolatileImage;
import sun.awt.image.ToolkitImage;
import sun.java2d.pipe.Region;

import java.awt.*;
import java.awt.BufferCapabilities.FlipContents;
import java.awt.dnd.DropTarget;
import java.awt.dnd.peer.DropTargetPeer;
import java.awt.event.PaintEvent;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.awt.image.VolatileImage;
import java.awt.peer.ComponentPeer;
import java.awt.peer.ContainerPeer;

public class GComponentPeer extends GObjectPeer implements ComponentPeer, DropTargetPeer {
    public GComponentPeer(Component target) {
        super(target);
    }

    @Override
    public void dispose() {
    }

    @Override
    public void addDropTarget(DropTarget dt) {
    }

    @Override
    public void removeDropTarget(DropTarget dt) {
    }

    @Override
    public boolean isObscured() {
        // false because canDetermineObscurity indicates we do not support this
        return false;
    }

    @Override
    public boolean canDetermineObscurity() {
        return false;
    }

    @Override
    public void setVisible(boolean v) {
        // nothing to do
    }

    @Override
    public void setEnabled(boolean e) {
        // nothing to do
    }

    @Override
    public void paint(Graphics g) {
    }

    @Override
    public void print(Graphics g) {
        // nothing to do
    }

    @Override
    public void setBounds(int x, int y, int width, int height, int op) {
        // nothing to do
    }

    @Override
    public void handleEvent(AWTEvent e) {
        // nothing to do
    }

    @Override
    public void coalescePaintEvent(PaintEvent e) {
        // nothing to do
    }

    @Override
    public Point getLocationOnScreen() {
        return new Point();
    }

    @Override
    public Dimension getPreferredSize() {
        return getMinimumSize();
    }

    @Override
    public Dimension getMinimumSize() {
        return ((Component) target).getSize();
    }

    @Override
    public ColorModel getColorModel() {
        return null;
    }

   // @Override
    public Toolkit getToolkit() {
        return Toolkit.getDefaultToolkit();
    }

    @Override
    public Graphics getGraphics() {
        return null;
    }

    protected void disposeImpl() {
    }

    @Override
    public FontMetrics getFontMetrics(Font font) {
        return getToolkit().getFontMetrics(font);
    }

    @Override
    public synchronized void setForeground(Color c) {
    }

    @Override
    public synchronized void setBackground(Color c) {
    }

    @Override
    public synchronized void setFont(Font f) {
    }

    @Override
    public void updateCursorImmediately() {
    }

    @Override
    public boolean requestFocus(Component lightweightChild, boolean temporary, boolean focusedWindowChangeAllowed, long time, Cause cause) {
        return false;
    }

    @Override
    public boolean isFocusable() {
        return false;
    }

    @Override
    public Image createImage(ImageProducer producer) {
        return new ToolkitImage(producer);
    }

    @Override
    public Image createImage(int width, int height) {
        return new SunVolatileImage((Component) target, width, height);
    }

    @Override
    public VolatileImage createVolatileImage(int width, int height) {
        return new SunVolatileImage((Component) target, width, height);
    }

    @Override
    public boolean prepareImage(Image img, int w, int h, ImageObserver o) {
        return getToolkit().prepareImage(img, w, h, o);
    }

    @Override
    public int checkImage(Image img, int w, int h, ImageObserver o) {
        return getToolkit().checkImage(img, w, h, o);
    }

    @Override
    public GraphicsConfiguration getGraphicsConfiguration() {
        return ((Component) target).getGraphicsConfiguration();
    }

    @Override
    public boolean handlesWheelScrolling() {
        return false;
    }

    @Override
    public void createBuffers(int numBuffers, BufferCapabilities caps) throws AWTException {
    }

    @Override
    public Image getBackBuffer() {
        throw new IllegalStateException("Buffers have not been created");
    }

    @Override
    public void flip(int x1, int y1, int x2, int y2, FlipContents flipAction) {
    }

    @Override
    public void destroyBuffers() {
    }

    @Override
    public void reparent(ContainerPeer newContainer) {
    }

    @Override
    public boolean isReparentSupported() {
        return false;
    }

    @Override
    public void layout() {
    }

    @Override
    public void applyShape(Region shape) {
    }

    @Override
    public void setZOrder(ComponentPeer above) {
    }

    @Override
    public boolean updateGraphicsData(GraphicsConfiguration gc) {
        return false;
    }
}