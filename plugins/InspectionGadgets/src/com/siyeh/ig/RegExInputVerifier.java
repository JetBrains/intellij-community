package com.siyeh.ig;

import com.intellij.openapi.ui.Messages;

import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.SwingUtilities;
import java.text.ParseException;

/**
 * @author <A href="bas@carp-technologies.nl">Bas Leijdekkers</a>
 */
public class RegExInputVerifier extends InputVerifier {
    public boolean verify(JComponent input) {
        return true;
    }


    public boolean shouldYieldFocus(JComponent input) {
        if (input instanceof JFormattedTextField) {
            final JFormattedTextField ftf = (JFormattedTextField) input;
            final JFormattedTextField.AbstractFormatter formatter = ftf.getFormatter();
            if (formatter != null) {
                try {
                    formatter.stringToValue(ftf.getText());
                } catch (final ParseException e) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            Messages.showErrorDialog(e.getMessage(), "Malformed Naming Pattern");
                        }
                    });
                }
            }

        }
        return true;
    }
}