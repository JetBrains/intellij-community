import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
public class Test {
  public static void main(String[] args){
    ActionListener listener = new ActionListener(){
      {
        final String test = "problem reported twice";
      }
      public void actionPerformed(final ActionEvent e) {

      }
    };
  }
}