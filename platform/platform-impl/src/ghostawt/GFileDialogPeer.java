// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package ghostawt;

import java.awt.*;
import java.awt.peer.FileDialogPeer;
import java.io.FilenameFilter;
import java.util.List;

public class GFileDialogPeer extends GWindowPeer implements FileDialogPeer {
    public GFileDialogPeer(FileDialog target) {
        super(target);
    }

    @Override
    public void blockWindows(List<Window> windows) {
    }

    @Override
    public void setFile(String file) {
    }

    @Override
    public void setDirectory(String dir) {
    }

    @Override
    public void setFilenameFilter(FilenameFilter filter) {
    }

    @Override
    public void setTitle(String title) {
    }

    @Override
    public void setResizable(boolean resizeable) {
    }
}