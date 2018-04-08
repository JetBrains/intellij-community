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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInspection.InspectionProfileEntry;

public enum DescriptionType {

  INTENTION(IntentionAction.class.getName(), "intentionDescriptions", true),
  INSPECTION(InspectionProfileEntry.class.getName(), "inspectionDescriptions", false),
  POSTFIX_TEMPLATES(PostfixTemplate.class.getName(), "postfixTemplates", true);

  private final String myClassName;
  private final String myDescriptionFolder;
  private final boolean myFixedDescriptionFilename;

  DescriptionType(String className,
                  String descriptionFolder,
                  boolean fixedDescriptionFilename) {
    myFixedDescriptionFilename = fixedDescriptionFilename;
    myClassName = className;
    myDescriptionFolder = descriptionFolder;
  }

  public String getClassName() {
    return myClassName;
  }

  public String getDescriptionFolder() {
    return myDescriptionFolder;
  }

  public boolean isFixedDescriptionFilename() {
    return myFixedDescriptionFilename;
  }
}
