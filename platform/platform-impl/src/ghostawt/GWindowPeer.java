// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.awt.*;
import java.awt.peer.WindowPeer;

public class GWindowPeer extends GPanelPeer implements WindowPeer {
    public GWindowPeer(Component target) {
        super(target);
    }

    @Override
    public void toFront() {
    }

    @Override
    public void toBack() {
    }

    @Override
    public void updateAlwaysOnTopState() {
    }

    @Override
    public void updateFocusableWindowState() {
    }

    @Override
    public void setModalBlocked(Dialog blocker, boolean blocked) {
    }

    @Override
    public void updateMinimumSize() {
    }

    @Override
    public void updateIconImages() {
    }

    @Override
    public void setOpacity(float opacity) {
    }

    @Override
    public void setOpaque(boolean isOpaque) {
    }

    @Override
    public void updateWindow() {
    }

    @Override
    public void repositionSecurityWarning() {
    }
}
