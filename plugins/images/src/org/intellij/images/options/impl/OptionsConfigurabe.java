/*
 * Copyright 2004-2005 Alexey Efimov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.images.options.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.BaseConfigurableWithChangeSupport;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import org.intellij.images.ImagesBundle;
import org.intellij.images.options.Options;
import org.intellij.images.options.OptionsManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Configurable for Options.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public final class OptionsConfigurabe extends BaseConfigurableWithChangeSupport implements SearchableConfigurable, ApplicationComponent, PropertyChangeListener {
    @NonNls private static final String NAME = "Images.OptionsConfigurable";
    private static final String DISPLAY_NAME = ImagesBundle.message("settings.page.name");
    private OptionsUIForm uiForm;

    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    public Icon getIcon() {
        return IconLoader.getIcon("/org/intellij/images/icons/ImagesConfigurable.png");
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

    @NotNull
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

  @NonNls
  public String getId() {
    return "Images";
  }

  public boolean clearSearch() {
    return false;
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}
