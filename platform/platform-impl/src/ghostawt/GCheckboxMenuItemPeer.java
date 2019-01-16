// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.awt.*;
import java.awt.peer.CheckboxMenuItemPeer;

public class GCheckboxMenuItemPeer extends GMenuItemPeer implements CheckboxMenuItemPeer {
    public GCheckboxMenuItemPeer(CheckboxMenuItem target) {
        super(target);
    }

    @Override
    public void setState(boolean t) {
    }
}