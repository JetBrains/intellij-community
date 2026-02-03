import javax.swing.JPanel;
import javax.swing.JButton;

public class EDTSwingComponent {
  public void testMethod() {
    JPanel panel = new JPanel();
    panel.setVisible(true);    // Requires EDT
    panel.repaint();           // Safe method - no requirement

    setupButton();
  }

  private void setupButton() {
    JButton button = new JButton("Click");
    button.setText("Updated");
    button.revalidate();
  }
}
