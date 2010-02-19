import java.awt.event.ActionListener
import java.awt.event.ActionEvent

def x=new ActionListener() {
  def actionPerformed(ActionEvent e) {

  }

  <error descr="anonymous class cannot have abstract method">abstract</error> void foo();
}

