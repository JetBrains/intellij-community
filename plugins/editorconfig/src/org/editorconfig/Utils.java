package org.editorconfig;

import java.util.List;

import org.editorconfig.core.EditorConfig.OutPair;

public class Utils {
    public static String configValueForKey(List<OutPair> outPairs, String key) {
        for (OutPair outPair: outPairs) {
            if (outPair.getKey().equals(key)) {
                return outPair.getVal();
            }
        }
        return "";
    }

    public static String invalidConfigMessage(String configValue, String configKey, String filePath) {
        return "\"" + configValue + "\" is not a valid value for " + configKey + " for file " + filePath;
    }

    public static String appliedConfigMessage(String configValue, String configKey, String filePath) {
        return "Applied \"" + configValue + "\" as " + configKey + " for file " + filePath;
    }
}
