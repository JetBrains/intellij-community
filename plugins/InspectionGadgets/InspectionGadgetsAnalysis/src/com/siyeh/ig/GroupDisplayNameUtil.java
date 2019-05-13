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

import com.intellij.codeInspection.InspectionsBundle;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.Map;

public class GroupDisplayNameUtil {
  @NonNls
  private static final Map<String, String> packageGroupDisplayNameMap = new HashMap<>();

  static {
    packageGroupDisplayNameMap.put("abstraction", "group.names.abstraction.issues");
    packageGroupDisplayNameMap.put("assignment", "group.names.assignment.issues");
    packageGroupDisplayNameMap.put("bitwise", "group.names.bitwise.operation.issues");
    packageGroupDisplayNameMap.put("bugs", "group.names.probable.bugs");
    packageGroupDisplayNameMap.put("classlayout", "group.names.class.structure");
    packageGroupDisplayNameMap.put("classmetrics", "group.names.class.metrics");
    packageGroupDisplayNameMap.put("cloneable", "group.names.cloning.issues");
    packageGroupDisplayNameMap.put("controlflow", "group.names.control.flow.issues");
    packageGroupDisplayNameMap.put("dataflow", "group.names.data.flow.issues");
    packageGroupDisplayNameMap.put("dependency", "group.names.dependency.issues");
    packageGroupDisplayNameMap.put("encapsulation", "group.names.encapsulation.issues");
    packageGroupDisplayNameMap.put("errorhandling", "group.names.error.handling");
    packageGroupDisplayNameMap.put("finalization", "group.names.finalization.issues");
    packageGroupDisplayNameMap.put("imports", "group.names.imports");
    packageGroupDisplayNameMap.put("inheritance", "group.names.inheritance.issues");
    packageGroupDisplayNameMap.put("initialization", "group.names.initialization.issues");
    packageGroupDisplayNameMap.put("internationalization", "group.names.internationalization.issues");
    packageGroupDisplayNameMap.put("j2me", "group.names.j2me.issues");
    packageGroupDisplayNameMap.put("javabeans", "group.names.javabeans.issues");
    packageGroupDisplayNameMap.put("javadoc", "group.names.javadoc.issues");
    packageGroupDisplayNameMap.put("jdk", "group.names.java.language.level.issues");
    packageGroupDisplayNameMap.put("migration",
                                   "group.names.language.level.specific.issues.and.migration.aids");
    packageGroupDisplayNameMap.put("junit", "group.names.junit.issues");
    packageGroupDisplayNameMap.put("logging", "group.names.logging.issues");
    packageGroupDisplayNameMap.put("maturity", "group.names.code.maturity.issues");
    packageGroupDisplayNameMap.put("memory", "group.names.memory.issues");
    packageGroupDisplayNameMap.put("methodmetrics", "group.names.method.metrics");
    packageGroupDisplayNameMap.put("modularization", "group.names.modularization.issues");
    packageGroupDisplayNameMap.put("naming", "group.names.naming.conventions");
    packageGroupDisplayNameMap.put("numeric", "group.names.numeric.issues");
    packageGroupDisplayNameMap.put("packaging", "group.names.packaging.issues");
    packageGroupDisplayNameMap.put("performance", "group.names.performance.issues");
    packageGroupDisplayNameMap.put("portability", "group.names.portability.issues");
    packageGroupDisplayNameMap.put("redundancy", "group.names.declaration.redundancy");
    packageGroupDisplayNameMap.put("resources", "group.names.resource.management.issues");
    packageGroupDisplayNameMap.put("security", "group.names.security.issues");
    packageGroupDisplayNameMap.put("serialization", "group.names.serialization.issues");
    packageGroupDisplayNameMap.put("style", "group.names.code.style.issues");
    packageGroupDisplayNameMap.put("threading", "group.names.threading.issues");
    packageGroupDisplayNameMap.put("visibility", "group.names.visibility.issues");
  }

  private GroupDisplayNameUtil() {
  }

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
    return InspectionsBundle.message(groupDisplayName);
  }
}
