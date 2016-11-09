import java.net.URL;
import java.util.List;

import javafx.beans.property.SimpleListProperty;
import javafx.collections.ObservableList;
import javafx.scene.Node;

class ListDemo {
    private SimpleListProperty<URL> urls = new SimpleListProperty<>(this, "urls");

    ListDemo(List<URL> urls) {
        this.urls.setAll(urls);
    }

    public ObservableList<URL> getUrls() {
        return urls.get();
    }

    public void setUrls(List<URL> urls) {
        this.urls.setAll(urls);
    }

    public String toString() {
        return "urls=" + urls.get();
    }
}