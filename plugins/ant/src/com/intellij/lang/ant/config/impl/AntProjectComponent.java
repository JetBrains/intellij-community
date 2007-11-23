/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
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
import com.intellij.openapi.components.ProjectComponent;
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AntProjectComponent implements ProjectComponent {


  public AntProjectComponent(final XmlAspect xmlAspect, PomModel pomModel, Project project) {
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

  public void projectOpened() {

  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return getClass().getName();
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}