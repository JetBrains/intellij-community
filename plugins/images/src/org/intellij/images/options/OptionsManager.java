package org.intellij.images.options;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;

/**
 * Options manager.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public abstract class OptionsManager {
    /**
     * Return current options.
     */
    public abstract Options getOptions();

    public static OptionsManager getInstance() {
        Application application = ApplicationManager.getApplication();
        return application.getComponent(OptionsManager.class);
    }
}
