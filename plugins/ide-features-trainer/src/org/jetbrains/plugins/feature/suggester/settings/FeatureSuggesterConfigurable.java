package org.jetbrains.plugins.feature.suggester.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Alefas
 * @since 25.05.13
 */
public class FeatureSuggesterConfigurable implements Configurable {
    private FeatureSuggesterForm myForm;
    private JPanel myPanel;

    public FeatureSuggesterConfigurable() {
        myForm = new FeatureSuggesterForm();
        myPanel = myForm.getPanel();
    }

    @Nls
    @Override
    public String getDisplayName() {
        return "Feature Suggester";
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return null;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        return myPanel;
    }

    @Override
    public boolean isModified() {
        return myForm.isModified();
    }

    @Override
    public void apply() throws ConfigurationException {
        myForm.apply();
    }

    @Override
    public void reset() {
        myForm.reset();
    }

    @Override
    public void disposeUIResources() {
    }
}
