import java.awt.event.ActionEvent
import java.awt.event.ActionListener

def x=new ActionListener() {
  def actionPerformed(ActionEvent e) {

  }

  <error descr="Anonymous class cannot have abstract method">abstract</error> void foo();
}

