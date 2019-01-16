// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.awt.*;
import java.awt.peer.ScrollPanePeer;

public class GScrollPanePeer extends GPanelPeer implements ScrollPanePeer {
    public GScrollPanePeer(Component target) {
        super(target);
    }

    @Override
    public int getHScrollbarHeight() {
        return 0;
    }

    @Override
    public int getVScrollbarWidth() {
        return 0;
    }

    @Override
    public void setScrollPosition(int x, int y) {
    }

    @Override
    public void childResized(int w, int h) {
    }

    @Override
    public void setUnitIncrement(Adjustable adj, int u) {
    }

    @Override
    public void setValue(Adjustable adj, int v) {
    }
}
