package ru.adelf.idea.dotenv.ui;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.DotEnvSettings;

import javax.swing.*;
import javax.swing.border.Border;

public class DotEnvSettingsConfigurable implements Configurable {

    private final Project project;

    public DotEnvSettingsConfigurable(@NotNull final Project project) {
        this.project = project;
    }

    private JCheckBox completionEnabledCheckbox;
    private JCheckBox storeValuesCheckbox;

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

        JLabel storeValuesInvalidateCachesLabel = new JLabel("Run File > Invalidate Caches... to update indices");
        storeValuesInvalidateCachesLabel.setBorder(standardBorder);
        storeValuesInvalidateCachesLabel.setVisible(false);

        storeValuesCheckbox.addChangeListener(e -> storeValuesInvalidateCachesLabel.setVisible(storeValuesCheckbox.isSelected() != getSettings().storeValues));

        JPanel rootPanel = new JPanel();
        rootPanel.setLayout(new BoxLayout(rootPanel, BoxLayout.PAGE_AXIS));
        rootPanel.add(completionEnabledCheckbox);
        rootPanel.add(storeValuesCheckbox);
        rootPanel.add(storeValuesInvalidateCachesLabel);

        return rootPanel;
    }

    @Override
    public boolean isModified() {
        return !completionEnabledCheckbox.isSelected() == getSettings().completionEnabled
            || !storeValuesCheckbox.isSelected() == getSettings().storeValues
            ;
    }

    @Override
    public void apply() {
        getSettings().completionEnabled = completionEnabledCheckbox.isSelected();
        getSettings().storeValues = storeValuesCheckbox.isSelected();
    }

    @Override
    public void reset() {
        completionEnabledCheckbox.setSelected(getSettings().completionEnabled);
        storeValuesCheckbox.setSelected(getSettings().storeValues);
    }

    private DotEnvSettings getSettings() {
        return DotEnvSettings.getInstance(this.project);
    }
}
