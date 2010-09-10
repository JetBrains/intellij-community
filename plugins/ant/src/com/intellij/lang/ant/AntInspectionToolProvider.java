/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.lang.ant.dom.AntResolveInspection;
import com.intellij.lang.ant.validation.AntDuplicateTargetsInspection;
import com.intellij.lang.ant.validation.AntMissingPropertiesFileInspection;

/**
 * @author yole
 */
public class AntInspectionToolProvider implements InspectionToolProvider {
  public Class[] getInspectionClasses() {
    return new Class[]{
      AntDuplicateTargetsInspection.class,  // todo: rewrite into dom inspections
      AntMissingPropertiesFileInspection.class,
      AntResolveInspection.class
    };
  }
}
