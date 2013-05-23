package org.jetbrains.plugins.feature.suggester.settings;

import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.plugins.feature.suggester.FeatureSuggester;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author Alefas
 * @since 25.05.13
 */
public class FeatureSuggesterForm {
    private JPanel myPanel;
    private JPanel innerPanel;
    private ArrayList<JCheckBox> checkBoxes = new ArrayList<JCheckBox>();

    public FeatureSuggesterForm() {
        final FeatureSuggester[] allSuggesters = FeatureSuggester.getAllSuggesters();
        innerPanel.setLayout(new GridLayout(allSuggesters.length, 1));
        for (FeatureSuggester suggester : allSuggesters) {
            final JCheckBox checkBox = new JCheckBox(suggester.getId(), FeatureSuggesterSettings.getInstance().isEnabled(suggester.getId()));
            innerPanel.add(checkBox);
            checkBoxes.add(checkBox);
        }
    }

    public JPanel getPanel() {
        return myPanel;
    }

    public boolean isModified() {
        for (JCheckBox checkBox : checkBoxes) {
            if (checkBox.isSelected() != FeatureSuggesterSettings.getInstance().isEnabled(checkBox.getText())) return true;
        }
        return false;
    }

    public void apply() throws ConfigurationException {
        ArrayList<String> buffer = new ArrayList<String>();
        for (JCheckBox checkBox : checkBoxes) {
            if (!checkBox.isSelected()) buffer.add(checkBox.getText());
        }
        FeatureSuggesterSettings.getInstance().DISABLED_SUGGESTERS = buffer.toArray(new String[buffer.size()]);
    }

    public void reset() {
        for (JCheckBox checkBox : checkBoxes) {
            checkBox.setSelected(FeatureSuggesterSettings.getInstance().isEnabled(checkBox.getText()));
        }
    }
}
