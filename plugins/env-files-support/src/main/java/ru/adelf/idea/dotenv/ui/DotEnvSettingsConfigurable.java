package ru.adelf.idea.dotenv.ui;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.DotEnvSettings;

import javax.swing.*;
import javax.swing.border.Border;

public class DotEnvSettingsConfigurable implements Configurable {

    private JCheckBox completionEnabledCheckbox;
    private JCheckBox storeValuesCheckbox;
    private JCheckBox hideValuesCheckbox;

    @Nls
    @Override
    public String getDisplayName() {
        return "DotEnv";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        DotEnvSettings settings = getSettings();
        Border standardBorder = BorderFactory.createEmptyBorder(5, 5, 5, 5);

        completionEnabledCheckbox = new JCheckBox("Enable environment variables completions", settings.completionEnabled);
        completionEnabledCheckbox.setBorder(standardBorder);

        storeValuesCheckbox = new JCheckBox("Store and complete values", settings.storeValues);
        storeValuesCheckbox.setBorder(standardBorder);
        storeValuesCheckbox.setToolTipText("Storing values in the indices can be turned off due to security reasons");

        JLabel storeValuesInvalidateCachesLabel = new JBLabel("Run File > Invalidate Caches... to update indices");
        storeValuesInvalidateCachesLabel.setBorder(standardBorder);
        storeValuesInvalidateCachesLabel.setVisible(false);

        storeValuesCheckbox.addChangeListener(e -> storeValuesInvalidateCachesLabel.setVisible(storeValuesCheckbox.isSelected() != getSettings().storeValues));

        hideValuesCheckbox = new JCheckBox("Hide values in .env files", settings.storeValues);
        hideValuesCheckbox.setBorder(standardBorder);

        JLabel hideValuesLabel = new JBLabel("<html>Check this if you want values to be hidden by default.<br>Main menu > Code > Folding actions can be used to control it.</html>");
        hideValuesLabel.setBorder(standardBorder);

        JPanel rootPanel = new JPanel();
        rootPanel.setLayout(new BoxLayout(rootPanel, BoxLayout.PAGE_AXIS));
        rootPanel.add(completionEnabledCheckbox);
        rootPanel.add(storeValuesCheckbox);
        rootPanel.add(storeValuesInvalidateCachesLabel);
        rootPanel.add(hideValuesCheckbox);
        rootPanel.add(hideValuesLabel);

        return rootPanel;
    }

    @Override
    public boolean isModified() {
        return !completionEnabledCheckbox.isSelected() == getSettings().completionEnabled
            || !storeValuesCheckbox.isSelected() == getSettings().storeValues
            || !hideValuesCheckbox.isSelected() == getSettings().hideValuesInTheFile
            ;
    }

    @Override
    public void apply() {
        DotEnvSettings settings = getSettings();

        settings.completionEnabled = completionEnabledCheckbox.isSelected();
        settings.storeValues = storeValuesCheckbox.isSelected();
        settings.hideValuesInTheFile = hideValuesCheckbox.isSelected();
    }

    @Override
    public void reset() {
        completionEnabledCheckbox.setSelected(getSettings().completionEnabled);
        storeValuesCheckbox.setSelected(getSettings().storeValues);
        hideValuesCheckbox.setSelected(getSettings().hideValuesInTheFile);
    }

    private DotEnvSettings getSettings() {
        return DotEnvSettings.getInstance();
    }
}
