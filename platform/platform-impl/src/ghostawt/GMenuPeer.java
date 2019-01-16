// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.awt.*;
import java.awt.peer.MenuPeer;

public class GMenuPeer extends GMenuItemPeer implements MenuPeer {
    protected GMenuPeer(Object target) {
        super(target);
    }
    
    public GMenuPeer(MenuItem target) {
        super(target);
    }

    @Override
    public void addSeparator() {
    }

    @Override
    public void addItem(MenuItem item) {
    }

    @Override
    public void delItem(int index) {
    }
}