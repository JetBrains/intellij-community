import javafx.event.ActionEvent;
import javafx.event.Event;

public class HighlightAmbiguous {
  public void onArgType(ActionEvent e) {}
  public void onArgType(Event e) {}
  public void onNoArg(ActionEvent e) {}
  public void onNoArg() {}
  public void onNotEvent(Event e) {}
  public void onNotEvent(String notEvent) {}
  public void onNotEvent(int notEvent) {}
}
