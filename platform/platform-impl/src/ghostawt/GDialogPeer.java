// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.awt.*;
import java.awt.peer.DialogPeer;
import java.util.List;

public class GDialogPeer extends GWindowPeer implements DialogPeer {
    public GDialogPeer(Component target) {
        super(target);
    }

    @Override
    public void setTitle(String title) {
    }

    @Override
    public void setResizable(boolean resizeable) {
    }

    @Override
    public void blockWindows(List<Window> windows) {
    }
}