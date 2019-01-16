// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.awt.*;
import java.awt.peer.ScrollbarPeer;

public class GScrollbarPeer extends GComponentPeer implements ScrollbarPeer {
    public GScrollbarPeer(Component target) {
        super(target);
    }

    @Override
    public void setValues(int value, int visible, int minimum, int maximum) {
    }

    @Override
    public void setLineIncrement(int l) {
    }

    @Override
    public void setPageIncrement(int l) {
    }
}