// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.awt.*;
import java.awt.im.InputMethodRequests;
import java.awt.peer.TextFieldPeer;

public class GTextFieldPeer extends GTextComponentPeer implements TextFieldPeer {
    public GTextFieldPeer(TextComponent target) {
        super(target);
    }

    @Override
    public InputMethodRequests getInputMethodRequests() {
        return null;
    }

    @Override
    public void setEchoChar(char echoChar) {
    }

    @Override
    public Dimension getPreferredSize(int columns) {
        return null;
    }

    @Override
    public Dimension getMinimumSize(int columns) {
        return null;
    }
}