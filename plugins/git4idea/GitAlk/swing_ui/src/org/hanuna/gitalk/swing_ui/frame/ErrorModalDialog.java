package org.hanuna.gitalk.swing_ui.frame;

import org.hanuna.gitalk.swing_ui.UI_Utilities;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * @author erokhins
 */
public class ErrorModalDialog extends JDialog {
    private final JLabel textLabel = new JLabel();

    public ErrorModalDialog(@NotNull String message) throws HeadlessException {
        setTitle("Fatal error");
        prepare(message);
    }

    private String prepareMessage(@NotNull String message) {
        return "<html>" + message.replace("\n", "<br>")+ "</html>";
    }

    private void prepare(@NotNull String message) {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setModalExclusionType(Dialog.ModalExclusionType.NO_EXCLUDE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        setModal(true);
        textLabel.setText(prepareMessage(message));
        JPanel panel = new JPanel();
        panel.add(textLabel);
        setContentPane(panel);

        updateUI();
    }

    private void updateUI() {
        pack();
        UI_Utilities.setCenterLocation(this);
    }

    public void setMessage(@NotNull String message) {
        textLabel.setText(prepareMessage(message));
        updateUI();
    }

}
