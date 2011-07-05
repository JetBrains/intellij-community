/*
 * Copyright 2011 Bas Leijdekkers
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

import com.intellij.codeInsight.daemon.GroupNames;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.Map;

public class GroupDisplayNameUtil {
    @NonNls
    private static final Map<String, String> packageGroupDisplayNameMap = new HashMap<String, String>();

    static {
        packageGroupDisplayNameMap.put("abstraction", GroupNames.ABSTRACTION_GROUP_NAME);
        packageGroupDisplayNameMap.put("assignment", GroupNames.ASSIGNMENT_GROUP_NAME);
        packageGroupDisplayNameMap.put("bitwise", GroupNames.BITWISE_GROUP_NAME);
        packageGroupDisplayNameMap.put("bugs", GroupNames.BUGS_GROUP_NAME);
        packageGroupDisplayNameMap.put("classlayout", GroupNames.CLASS_LAYOUT_GROUP_NAME);
        packageGroupDisplayNameMap.put("classmetrics", GroupNames.CLASS_METRICS_GROUP_NAME);
        packageGroupDisplayNameMap.put("cloneable", GroupNames.CLONEABLE_GROUP_NAME);
        packageGroupDisplayNameMap.put("controlflow", GroupNames.CONTROL_FLOW_GROUP_NAME);
        packageGroupDisplayNameMap.put("dataflow", GroupNames.DATA_FLOW_ISSUES);
        packageGroupDisplayNameMap.put("dependency", GroupNames.DEPENDENCY_GROUP_NAME);
        packageGroupDisplayNameMap.put("encapsulation", GroupNames.ENCAPSULATION_GROUP_NAME);
        packageGroupDisplayNameMap.put("errorhandling", GroupNames.ERROR_HANDLING_GROUP_NAME);
        packageGroupDisplayNameMap.put("finalization", GroupNames.FINALIZATION_GROUP_NAME);
        packageGroupDisplayNameMap.put("imports", GroupNames.IMPORTS_GROUP_NAME);
        packageGroupDisplayNameMap.put("inheritance", GroupNames.INHERITANCE_GROUP_NAME);
        packageGroupDisplayNameMap.put("initialization", GroupNames.INITIALIZATION_GROUP_NAME);
        packageGroupDisplayNameMap.put("internationalization", GroupNames.INTERNATIONALIZATION_GROUP_NAME);
        packageGroupDisplayNameMap.put("j2me", GroupNames.J2ME_GROUP_NAME);
        packageGroupDisplayNameMap.put("javabeans", GroupNames.JAVABEANS_GROUP_NAME);
        packageGroupDisplayNameMap.put("javadoc", GroupNames.JAVADOC_GROUP_NAME);
        packageGroupDisplayNameMap.put("jdk", GroupNames.JDK_GROUP_NAME);
        packageGroupDisplayNameMap.put("migration", GroupNames.LANGUAGE_LEVEL_SPECIFIC_GROUP_NAME);
        packageGroupDisplayNameMap.put("junit", GroupNames.JUNIT_GROUP_NAME);
        packageGroupDisplayNameMap.put("logging", GroupNames.LOGGING_GROUP_NAME);
        packageGroupDisplayNameMap.put("maturity", GroupNames.MATURITY_GROUP_NAME);
        packageGroupDisplayNameMap.put("memory", GroupNames.MEMORY_GROUP_NAME);
        packageGroupDisplayNameMap.put("methodmetrics", GroupNames.METHOD_METRICS_GROUP_NAME);
        packageGroupDisplayNameMap.put("modularization", GroupNames.MODULARIZATION_GROUP_NAME);
        packageGroupDisplayNameMap.put("naming", GroupNames.NAMING_CONVENTIONS_GROUP_NAME);
        packageGroupDisplayNameMap.put("numeric", GroupNames.NUMERIC_GROUP_NAME);
        packageGroupDisplayNameMap.put("packaging", GroupNames.PACKAGING_GROUP_NAME);
        packageGroupDisplayNameMap.put("performance", GroupNames.PERFORMANCE_GROUP_NAME);
        packageGroupDisplayNameMap.put("portability", GroupNames.PORTABILITY_GROUP_NAME);
        packageGroupDisplayNameMap.put("redundancy", GroupNames.DECLARATION_REDUNDANCY);
        packageGroupDisplayNameMap.put("resources", GroupNames.RESOURCE_GROUP_NAME);
        packageGroupDisplayNameMap.put("security", GroupNames.SECURITY_GROUP_NAME);
        packageGroupDisplayNameMap.put("serialization", GroupNames.SERIALIZATION_GROUP_NAME);
        packageGroupDisplayNameMap.put("style", GroupNames.STYLE_GROUP_NAME);
        packageGroupDisplayNameMap.put("threading", GroupNames.THREADING_GROUP_NAME);
        packageGroupDisplayNameMap.put("visibility", GroupNames.VISIBILITY_GROUP_NAME);
    }

    private GroupDisplayNameUtil() {}

    public static String getGroupDisplayName(Class<?> aClass) {
        final Package thisPackage = aClass.getPackage();
        assert thisPackage != null : "need package to determine group display name";
        final String name = thisPackage.getName();
        assert name != null :
                "inspection has default package, group display name cannot be determined";
        final int index = name.lastIndexOf('.');
        final String key = name.substring(index + 1);
        final String groupDisplayName = packageGroupDisplayNameMap.get(key);
        assert groupDisplayName != null : "No display name found for " + key;
        return groupDisplayName;
    }
}
