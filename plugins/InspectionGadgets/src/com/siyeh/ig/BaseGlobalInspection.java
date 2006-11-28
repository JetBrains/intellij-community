/*
 * Copyright 2006 Dave Griffith
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
package com.siyeh.ig;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NonNls;

public abstract class BaseGlobalInspection extends GlobalInspectionTool {

    private final String shortName = null;
    @NonNls private static final String INSPECTION = "Inspection";

    public String getShortName() {
        if (shortName == null) {
            final Class<? extends BaseGlobalInspection> aClass = getClass();
            final String name = aClass.getName();
            return name.substring(name.lastIndexOf((int)'.') + 1,
                    name.length() - BaseGlobalInspection.INSPECTION.length());
        }
        return shortName;
    }

    private String getPropertyPrefixForInspection() {
        final String shortName = getShortName();
        return getPrefix(shortName);
    }

    public static String getPrefix(String shortName) {
        final StringBuilder builder = new StringBuilder(shortName.length() + 10);
        builder.append(Character.toLowerCase(shortName.charAt(0)));
        for (int i = 1; i < shortName.length(); i++) {
            final char c = shortName.charAt(i);
            if (Character.isUpperCase(c)) {
                builder.append('.').append(Character.toLowerCase(c));
            }
            else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    public String getDisplayName() {
        @NonNls final String displayNameSuffix = ".display.name";
        return InspectionGadgetsBundle.message(getPropertyPrefixForInspection() +
                displayNameSuffix);
    }

    public boolean isGraphNeeded() {
        return true;
    }

    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WARNING;
    }

    public boolean isEnabledByDefault() {
        return false;
    }
}