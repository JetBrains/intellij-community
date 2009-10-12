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

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 24-Jul-2006
 * Time: 14:46:05
 */
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.AntLanguageExtension;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.changes.AntChangeVisitor;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomChangeSet;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.event.PomModelListener;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.XmlChangeSet;
import com.intellij.pom.xml.events.XmlChange;
import com.intellij.psi.xml.XmlFile;

public class AntProjectComponent extends AbstractProjectComponent {
  public AntProjectComponent(final XmlAspect xmlAspect, PomModel pomModel, Project project) {
    super(project);
    pomModel.addModelListener(new PomModelListener() {
      public void modelChanged(final PomModelEvent event) {
        final PomChangeSet changeSet = event.getChangeSet(xmlAspect);
        if (changeSet instanceof XmlChangeSet) {
          final XmlChangeSet xmlChangeSet = (XmlChangeSet)changeSet;

          for (XmlFile file : xmlChangeSet.getChangedFiles()) {
            if (file != null && AntLanguageExtension.isAntFile(file)) {
              final AntChangeVisitor visitor = AntSupport.getChangeVisitor();
              for (XmlChange change : xmlChangeSet.getChanges()) {
                change.accept(visitor);
              }

              break;
            }
          }
        }
      }

      public boolean isAspectChangeInteresting(final PomModelAspect aspect) {
        return aspect == xmlAspect;
      }
    }, project);
  }
}