package org.intellij.images.options.impl;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.BaseConfigurableWithChangeSupport;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.intellij.images.options.Options;
import org.intellij.images.options.OptionsManager;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Configurable for Options.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public final class OptionsConfigurabe extends BaseConfigurableWithChangeSupport implements ApplicationComponent, PropertyChangeListener {
    private static final String NAME = "Images.OptionsConfigurable";
    private static final String DISPLAY_NAME = "Images";
    private OptionsUIForm uiForm;

    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    public Icon getIcon() {
        return IconLoader.getIcon("/org/intellij/images/options/icons/ImagesConfigurable.png");
    }

    public String getHelpTopic() {
        return null;
    }

    public JComponent createComponent() {
        if (uiForm == null) {
            uiForm = new OptionsUIForm();
            Options options = OptionsManager.getInstance().getOptions();
            options.addPropertyChangeListener(this);
            uiForm.getOptions().inject(options);
            uiForm.updateUI();
            uiForm.getOptions().addPropertyChangeListener(this);
            setModified(false);
        }
        return uiForm.getContentPane();
    }

    public void apply() {
        if (uiForm != null) {
            Options options = OptionsManager.getInstance().getOptions();
            options.inject(uiForm.getOptions());
        }
    }

    public void reset() {
        if (uiForm != null) {
            Options options = OptionsManager.getInstance().getOptions();
            uiForm.getOptions().inject(options);
            uiForm.updateUI();
        }
    }

    public void disposeUIResources() {
        if (uiForm != null) {
            Options options = OptionsManager.getInstance().getOptions();
            options.removePropertyChangeListener(this);
            uiForm.getOptions().removePropertyChangeListener(this);
            uiForm = null;
        }
    }

    public void propertyChange(PropertyChangeEvent evt) {
        Options options = OptionsManager.getInstance().getOptions();
        Options uiOptions = uiForm.getOptions();

        setModified(!options.equals(uiOptions));
    }

    public String getComponentName() {
        return NAME;
    }

    public void initComponent() {
    }

    public void disposeComponent() {
    }

    public static void show(Project project) {
        Application application = ApplicationManager.getApplication();
        OptionsConfigurabe component = application.getComponent(OptionsConfigurabe.class);
        ShowSettingsUtil.getInstance().editConfigurable(project, component);
    }
}
