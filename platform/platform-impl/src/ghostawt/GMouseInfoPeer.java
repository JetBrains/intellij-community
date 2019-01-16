// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.awt.*;
import java.awt.peer.MouseInfoPeer;

public class GMouseInfoPeer implements MouseInfoPeer {
    @Override
    public int fillPointWithCoords(Point point) {
        return 0;
    }

    @Override
    public boolean isWindowUnderMouse(Window w) {
        return false;
    }
}
