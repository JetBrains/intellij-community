// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.awt.*;
import java.awt.im.InputMethodRequests;
import java.awt.peer.TextAreaPeer;

public class GTextAreaPeer extends GTextComponentPeer implements TextAreaPeer {
    public GTextAreaPeer(TextComponent target) {
        super(target);
    }

    @Override
    public InputMethodRequests getInputMethodRequests() {
        return null;
    }

    @Override
    public void insert(String text, int pos) {
    }

    @Override
    public void replaceRange(String text, int start, int end) {
    }

    @Override
    public Dimension getPreferredSize(int rows, int columns) {
        return null;
    }

    @Override
    public Dimension getMinimumSize(int rows, int columns) {
        return null;
    }
}