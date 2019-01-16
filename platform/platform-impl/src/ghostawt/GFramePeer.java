// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.awt.*;
import java.awt.peer.FramePeer;

public class GFramePeer extends GWindowPeer implements FramePeer {
    public GFramePeer(Component target) {
        super(target);
    }

    @Override
    public void setTitle(String title) {
    }

    @Override
    public void setMenuBar(MenuBar mb) {
    }

    @Override
    public void setResizable(boolean resizeable) {
    }

    @Override
    public void setState(int state) {
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public void setMaximizedBounds(Rectangle bounds) {
    }

    @Override
    public void setBoundsPrivate(int x, int y, int width, int height) {
    }

    @Override
    public Rectangle getBoundsPrivate() {
        return null;
    }

    @Override
    public void emulateActivation(boolean b) {

    }
}