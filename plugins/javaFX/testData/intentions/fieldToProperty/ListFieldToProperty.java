import java.net.URL;
import java.util.List;
import javafx.scene.Node;

class ListDemo {
    private List<URL> <caret>urls;

    ListDemo(List<URL> urls) {
        this.urls = urls;
    }

    public List<URL> getUrls() {
        return urls;
    }

    public void setUrls(List<URL> urls) {
        this.urls = urls;
    }

    public String toString() {
        return "urls=" + urls;
    }
}