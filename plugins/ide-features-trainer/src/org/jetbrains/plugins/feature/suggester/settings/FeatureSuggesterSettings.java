package org.jetbrains.plugins.feature.suggester.settings;

import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;

@State(
        name = "FeatureSuggesterSettings",
        storages = {
                @Storage(id = "default", file = StoragePathMacros.APP_CONFIG + "/FeatureSuggester.xml")
        }
)
public class FeatureSuggesterSettings implements PersistentStateComponent<FeatureSuggesterSettings> {
    public String[] DISABLED_SUGGESTERS = new String[0];

    public boolean isEnabled(String id) {
        for (String s : DISABLED_SUGGESTERS) {
            if (s.equals(id)) return false;
        }
        return true;
    }

    public FeatureSuggesterSettings getState() {
        return this;
    }

    public void loadState(FeatureSuggesterSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public static FeatureSuggesterSettings getInstance() {
        return ServiceManager.getService(FeatureSuggesterSettings.class);
    }
}