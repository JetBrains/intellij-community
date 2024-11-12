package ru.adelf.idea.dotenv;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "DotEnvSettings", storages = {@Storage("dot-env.xml")})
public class DotEnvSettings implements PersistentStateComponent<DotEnvSettings> {
    public boolean completionEnabled = true;
    public boolean storeValues = true;

    public boolean hideValuesInTheFile = false;

    @Nullable
    @Override
    public DotEnvSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull DotEnvSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public static DotEnvSettings getInstance() {
        return ApplicationManager.getApplication().getService(DotEnvSettings.class);
    }
}
