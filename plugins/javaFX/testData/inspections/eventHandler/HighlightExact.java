import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.input.ScrollEvent;

public class HighlightExact {
    public void onSameArg(ActionEvent e) {}
    public void onSuperArg(Event e) {}
    public void onNoArg() {}
    public String onSameArgNotVoid(ActionEvent e) {return "";}
    public Boolean onSuperArgNotVoid(Event e) {return false;}
    public int onNoArgNotVoid() {return 1;}
    public void onIncompatible(ScrollEvent e) {}
}
