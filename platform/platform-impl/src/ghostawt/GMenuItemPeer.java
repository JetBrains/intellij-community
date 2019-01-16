// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.awt.*;
import java.awt.peer.MenuItemPeer;

public class GMenuItemPeer extends GObjectPeer implements MenuItemPeer {
    protected GMenuItemPeer(Object target) {
        super(target);
    }

    public GMenuItemPeer(MenuItem target) {
        super(target);
    }

    @Override
    public void dispose() {
    }

    @Override
    public void setFont(Font f) {
    }

    @Override
    public void setLabel(String label) {
    }

    @Override
    public void setEnabled(boolean e) {
    }
}
