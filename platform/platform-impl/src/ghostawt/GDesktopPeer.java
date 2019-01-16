package ghostawt;

import java.awt.Desktop.Action;
import java.awt.peer.DesktopPeer;
import java.io.File;
import java.io.IOException;
import java.net.URI;

public class GDesktopPeer implements DesktopPeer {
    @Override
    public boolean isSupported(Action action) {
        return false;
    }

    @Override
    public void open(File file) throws IOException {
    }

    @Override
    public void edit(File file) throws IOException {
    }

    @Override
    public void print(File file) throws IOException {
    }

    @Override
    public void mail(URI mailtoURL) throws IOException {
    }

    @Override
    public void browse(URI url) throws IOException {
    }
}