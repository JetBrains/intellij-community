// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.awt.*;
import java.awt.peer.MenuBarPeer;

public class GMenuBarPeer extends GMenuPeer implements MenuBarPeer {
    public GMenuBarPeer(MenuBar target) {
        super(target);
    }

    @Override
    public void addMenu(Menu m) {
    }

    @Override
    public void delMenu(int index) {
    }

    @Override
    public void addHelpMenu(Menu m) {
    }
}