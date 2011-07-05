/*
 * Copyright 2006-2011 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.GlobalJavaInspectionTool;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class BaseGlobalInspection extends GlobalJavaInspectionTool {

    private String shortName = null;
    @NonNls private static final String INSPECTION = "Inspection";

    @Override
    @NotNull
    public String getShortName() {
        if (shortName == null) {
            final Class<? extends BaseGlobalInspection> aClass = getClass();
            final String name = aClass.getName();
            assert name.endsWith(INSPECTION) :
                    "class name must end with 'Inspection' to correctly" +
                            " calculate the short name: " + name;
            shortName = name.substring(name.lastIndexOf((int)'.') + 1,
                    name.length() - INSPECTION.length());
        }
        return shortName;
    }

    @Override @Nls
    @NotNull
    public final String getGroupDisplayName() {
        return GroupDisplayNameUtil.getGroupDisplayName(getClass());
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }
}