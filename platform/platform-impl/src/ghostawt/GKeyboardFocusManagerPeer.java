// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import sun.awt.KeyboardFocusManagerPeerImpl;

import java.awt.*;

public class GKeyboardFocusManagerPeer extends KeyboardFocusManagerPeerImpl {
    @Override
    public void setCurrentFocusedWindow(Window win) {
        // Not used on Windows
        throw new RuntimeException("not implemented");
    }

    @Override
    public Window getCurrentFocusedWindow() {
        return null;
    }

    private Component focusOwner;

    @Override
    public void setCurrentFocusOwner(Component comp) {
        focusOwner = comp;
    }

    @Override
    public Component getCurrentFocusOwner() {
        return focusOwner;
    }
}