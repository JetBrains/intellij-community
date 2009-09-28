import java.awt.event.ActionListener
import java.awt.event.ActionEvent

def x=new ActionListener() {
  def actionPerformed(ActionEvent e) {

  }

  <error descr="Inner classes cannot have static declarations">static</error> void foo(){

  }
}