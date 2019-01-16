// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.awt.*;
import java.awt.peer.TextComponentPeer;

public abstract class GTextComponentPeer extends GComponentPeer implements TextComponentPeer {
    private String text;

    public GTextComponentPeer(TextComponent target) {
        super(target);
    }

    @Override
    public void setEditable(boolean editable) {
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public void setText(String l) {
        text = l;
    }

    @Override
    public int getSelectionStart() {
        return 0;
    }

    @Override
    public int getSelectionEnd() {
        return 0;
    }

    @Override
    public void select(int selStart, int selEnd) {
    }

    @Override
    public void setCaretPosition(int pos) {
    }

    @Override
    public int getCaretPosition() {
        return 0;
    }
}