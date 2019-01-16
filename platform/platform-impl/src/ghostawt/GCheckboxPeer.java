// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.awt.*;
import java.awt.peer.CheckboxPeer;

public class GCheckboxPeer extends GComponentPeer implements CheckboxPeer {
    GCheckboxPeer(Component target) {
        super(target);
    }

    @Override
    public void setState(boolean state) {
    }

    @Override
    public void setCheckboxGroup(CheckboxGroup g) {
    }

    @Override
    public void setLabel(String label) {
    }
}