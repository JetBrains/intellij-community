/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.i18n.inconsistentResourceBundle;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.util.containers.BidirectionalMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public interface InconsistentResourceBundleInspectionProvider {
  @NotNull
  String getName();

  @NotNull
  String getPresentableName();

  void check(BidirectionalMap<PropertiesFile, PropertiesFile> parents,
             List<PropertiesFile> files,
             Map<PropertiesFile, Set<String>> keysUpToParent,
             Map<PropertiesFile, Map<String, String>> propertiesFilesNamesMaps,
             InspectionManager manager,
             RefManager refManager,
             ProblemDescriptionsProcessor processor);
}
