// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.awt.*;
import java.awt.peer.ButtonPeer;

public class GButtonPeer extends GComponentPeer implements ButtonPeer {
    public GButtonPeer(Button target) {
        super(target);
    }

    @Override
    public void setLabel(String label) {
    }
}
