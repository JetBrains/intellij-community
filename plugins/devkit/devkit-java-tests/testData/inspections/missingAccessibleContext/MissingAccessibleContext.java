import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;

class X implements ListCellRenderer<String> {
  public Component getListCellRendererComponent(
    JList<? extends String> list,
    String value,
    int index,
    boolean isSelected,
    boolean cellHasFocus) {
    return <warning descr="Accessible context is not defined for JPanel">new JPanel()</warning>;
  }
  
  void lambda() {
    ListCellRenderer<Integer> renderer = (list, val, index, sel, cell) -> {
      JPanel panel = <warning descr="Accessible context is not defined for JPanel">new JPanel() {}</warning>;
      return panel;
    };
  }
  
  void lambdaSubClass() {
    ListCellRenderer<Integer> renderer = (list, val, index, sel, cell) -> {
      var panel = <warning descr="Accessible context is not defined for JPanel">new MyJPanelBad()</warning>;
      return panel;
    };
  }
  
  void lambdaTernary() {
    ListCellRenderer<Integer> renderer = (list, val, index, sel, cell) -> {
      var panel = (index > 0 ? (new MyJPanel()) : (<warning descr="Accessible context is not defined for JPanel">new MyJPanelBad()</warning>));
      return sel ? panel : <warning descr="Accessible context is not defined for JPanel">new JPanel()</warning>;
    };
  }
  
  void lambdaTwoAssignments() {
    ListCellRenderer<Integer> renderer = (list, val, index, sel, cell) -> {
      JPanel panel;
      if (index > 0) {
        panel = <warning descr="Accessible context is not defined for JPanel">new JPanel()</warning>;
        panel.setEnabled(true);
      } else {
        panel = <warning descr="Accessible context is not defined for JPanel">new JPanel()</warning>;
        panel.setEnabled(false);
      }
      return panel;
    };
  }
  
  void lambdaTwoReturns() {
    ListCellRenderer<Integer> renderer = (list, val, index, sel, cell) -> {
      JPanel panel;
      if (index > 0) {
        panel = <warning descr="Accessible context is not defined for JPanel">new JPanel()</warning>;
        panel.setEnabled(true);
        return panel;
      } else {
        panel = <warning descr="Accessible context is not defined for JPanel">new JPanel()</warning>;
        panel.setEnabled(false);
        return panel;
      }
    };
  }

  void lambdaTwoReturnsSameJPanel() {
    ListCellRenderer<Integer> renderer = (list, val, index, sel, cell) -> {
      JPanel panel = <warning descr="Accessible context is not defined for JPanel">new JPanel()</warning>;
      if (index > 0) {
        panel.setEnabled(true);
        return panel;
      } else {
        panel.setEnabled(false);
        return panel;
      }
    };
  }

  void lambdaTwoReturnsSeparateVars() {
    ListCellRenderer<Integer> renderer = (list, val, index, sel, cell) -> {
      if (index > 0) {
        JPanel panel;
        panel = <warning descr="Accessible context is not defined for JPanel">new JPanel()</warning>;
        panel.setEnabled(true);
        return panel;
      } else {
        JPanel panel = <warning descr="Accessible context is not defined for JPanel">new JPanel()</warning>;
        panel.setEnabled(false);
        return panel;
      }
    };
  }
  
  void lambdaOk() {
    ListCellRenderer<Integer> renderer = (list, val, index, sel, cell) -> {
      JPanel panel = new JPanel() {
        public <error descr="'getAccessibleContext()' in 'Anonymous class derived from javax.swing.JPanel' clashes with 'getAccessibleContext()' in 'javax.swing.JPanel'; attempting to use incompatible return type">AccessibleContext</error> getAccessibleContext() {
          return null;
        }
      };
      return panel;
    };
  }

  private class MyJPanelBad extends JPanel {
    
  }
  
  private class MyJPanel extends JPanel {
    public <error descr="'getAccessibleContext()' in 'X.MyJPanel' clashes with 'getAccessibleContext()' in 'javax.swing.JPanel'; attempting to use incompatible return type">AccessibleContext</error> getAccessibleContext() {
      return null;
    }
  }
  
  void lambdaOk2() {
    ListCellRenderer<Integer> renderer = (list, val, index, sel, cell) -> {
      JPanel panel = new MyJPanel();
      return panel;
    };
  }
  
  void lambdaOk3() {
    ListCellRenderer<Integer> renderer = (list, val, index, sel, cell) -> {
      JPanel panel = new JPanel();
      panel.<error descr="Cannot resolve method 'setAccessibleName' in 'JPanel'">setAccessibleName</error>("foo");
      return panel;
    };
  }
}