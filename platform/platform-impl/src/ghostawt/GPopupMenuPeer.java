// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.awt.*;
import java.awt.peer.PopupMenuPeer;

public class GPopupMenuPeer extends GMenuPeer implements PopupMenuPeer {
    public GPopupMenuPeer(PopupMenu target) {
        super(target);
    }

    @Override
    public void show(Event e) {
    }
}