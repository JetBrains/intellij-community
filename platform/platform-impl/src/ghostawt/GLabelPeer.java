// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.awt.*;
import java.awt.peer.LabelPeer;

public class GLabelPeer extends GComponentPeer implements LabelPeer {
    public GLabelPeer(Component target) {
        super(target);
    }

    @Override
    public void setText(String label) {
    }

    @Override
    public void setAlignment(int alignment) {
    }
}