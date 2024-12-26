package ru.adelf.idea.dotenv.ui;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.DotEnvBundle;
import ru.adelf.idea.dotenv.DotEnvSettings;

import javax.swing.*;
import javax.swing.border.Border;

public class DotEnvSettingsConfigurable implements Configurable {

    private JCheckBox completionEnabledCheckbox;
    private JCheckBox storeValuesCheckbox;
    private JCheckBox hideValuesCheckbox;

    @Override
    public @Nls String getDisplayName() {
        return "DotEnv";
    }

    @Override
    public @Nullable JComponent createComponent() {
        DotEnvSettings settings = getSettings();
        Border standardBorder = BorderFactory.createEmptyBorder(5, 5, 5, 5);

        completionEnabledCheckbox = new JCheckBox(DotEnvBundle.message("enable.environment.variables.completions"), settings.completionEnabled);
        completionEnabledCheckbox.setBorder(standardBorder);

        storeValuesCheckbox = new JCheckBox(DotEnvBundle.message("store.and.complete.values"), settings.storeValues);
        storeValuesCheckbox.setBorder(standardBorder);
        storeValuesCheckbox.setToolTipText(DotEnvBundle.message("storing.values.in.the.indices.can.be.turned.off.due.to.security.reasons"));

        JLabel storeValuesInvalidateCachesLabel = new JBLabel(DotEnvBundle.message("label.run.file.invalidate.caches.to.update.indices"));
        storeValuesInvalidateCachesLabel.setBorder(standardBorder);
        storeValuesInvalidateCachesLabel.setVisible(false);

        storeValuesCheckbox.addChangeListener(e -> storeValuesInvalidateCachesLabel.setVisible(storeValuesCheckbox.isSelected() != getSettings().storeValues));

        hideValuesCheckbox = new JCheckBox(DotEnvBundle.message("hide.values.in.env.files"), settings.storeValues);
        hideValuesCheckbox.setBorder(standardBorder);

        JLabel hideValuesLabel = new JBLabel(
            DotEnvBundle.message(
                "label.check.this.if.you.want.values.to.be.hidden.by.default.br.main.menu.code.folding.actions.can.be.used.to.control.it"));
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

    private static DotEnvSettings getSettings() {
        return DotEnvSettings.getInstance();
    }
}
