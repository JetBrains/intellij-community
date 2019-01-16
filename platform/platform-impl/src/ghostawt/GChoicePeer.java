// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.awt.*;
import java.awt.peer.ChoicePeer;

public class GChoicePeer extends GComponentPeer implements ChoicePeer {
    GChoicePeer(Component target) {
        super(target);
    }

    @Override
    public void add(String item, int index) {
    }

    @Override
    public void remove(int index) {
    }

    @Override
    public void removeAll() {
    }

    @Override
    public void select(int index) {
    }
}