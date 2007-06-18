/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.*;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 13, 2007
 */
public class PropertiesBuilder extends AntElementVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.psi.impl.PropertiesBuilder");

  @NotNull private final AntFile myPropertyHolder;
  private Set<AntTarget> myVisitedTargets = new HashSet<AntTarget>();
  private Set<AntFile> myVisitedFiles = new HashSet<AntFile>();
  private Map<AntProject, List<Runnable>> myPostponedProcessing = new HashMap<AntProject, List<Runnable>>();
  private List<PsiFile> myDependentFiles = new ArrayList<PsiFile>();
  
  private PropertiesBuilder(@NotNull AntFile propertyHolder) {
    myPropertyHolder = propertyHolder;
  }

  public void visitAntTypedef(final AntTypeDef def) {
    // at this point all properties used in classpath for this typedef must be defined
    // so to make sure the class set is complete, need to reload classes here
    def.clearClassesCache();
    def.getDefinitions();
  }

  public void visitAntFile(final AntFile antFile) {
    if (!myVisitedFiles.contains(antFile)) {
      myVisitedFiles.add(antFile);
      final AntProject project = antFile.getAntProject();
      if (project != null) {
        project.acceptAntElementVisitor(this);
      }
    }
  }

  public void visitAntTask(final AntTask task) {
    if (task instanceof AntProperty) {
      visitAntProperty((AntProperty)task);
    }
    else {
      super.visitAntTask(task);
    }
  }

  public void visitAntProject(final AntProject antProject) {
    final Set<AntTarget> projectTargets = new LinkedHashSet<AntTarget>();
    for (PsiElement child : antProject.getChildren()) {
      if (child instanceof AntElement) {
        if (child instanceof AntTarget) {
          final AntTarget antTarget = (AntTarget)child;
          if (antProject.equals(antTarget.getAntProject())) {
            // heuristic: do not collect imported targets
            projectTargets.add(antTarget);
          }
        }
        else {
          ((AntElement)child).acceptAntElementVisitor(this);
        }
      }
    }

    final AntTarget entryTarget = antProject.getDefaultTarget();
    if (entryTarget != null) {
      entryTarget.acceptAntElementVisitor(this);
    }

    projectTargets.removeAll(myVisitedTargets);
    // process unvisited targets
    for (AntTarget antTarget : projectTargets) {
      antTarget.acceptAntElementVisitor(this);
    }
    // process postponed targets
    final List<Runnable> list = myPostponedProcessing.get(antProject);
    if (list != null) {
      for (Runnable runnable : list) {
        runnable.run();
      }
      myPostponedProcessing.remove(antProject);
    }
  }

  public void visitAntProperty(final AntProperty antProperty) {
    final PropertiesFile propertiesFile = antProperty.getPropertiesFile();
    if (propertiesFile != null) {
      myDependentFiles.add(propertiesFile);
    }

    final String environment = antProperty.getEnvironment();
    if (environment != null) {
      myPropertyHolder.addEnvironmentPropertyPrefix(environment);
    }

    final String[] names = antProperty.getNames();
    if (names != null) {
      for (String name : names) {
        myPropertyHolder.setProperty(name, antProperty);
      }
    }
  }

  public void visitAntTarget(final AntTarget target) {
    if (myVisitedTargets.contains(target)) {
      return;
    }
    myVisitedTargets.add(target);
    
    final AntTarget[] dependsTargets = target.getDependsTargets();
    for (AntTarget dependsTarget : dependsTargets) {
      dependsTarget.acceptAntElementVisitor(this);
    }

    final String ifProperty = target.getConditionalPropertyName(AntTarget.ConditionalAttribute.IF);
    if (ifProperty != null && myPropertyHolder.getProperty(ifProperty) == null) {
      postponeTargetVisiting(target);
      return; // skip target because 'if' property not defined
    }

    final String unlessProperty = target.getConditionalPropertyName(AntTarget.ConditionalAttribute.UNLESS);
    if (unlessProperty != null && myPropertyHolder.getProperty(unlessProperty) != null) {
      postponeTargetVisiting(target);
      return; // skip target because 'unless' property is defined 
    }

    visitTargetChildren(target);
  }

  private void postponeTargetVisiting(final AntTarget target) {
    final AntProject antProject = target.getAntProject();
    List<Runnable> list = myPostponedProcessing.get(antProject);
    if (list == null) {
      list = new ArrayList<Runnable>();
      myPostponedProcessing.put(antProject, list);
    }
    list.add(new Runnable() {
      public void run() {
        visitTargetChildren(target);
      }
    });
  }

  private void visitTargetChildren(final AntTarget target) {
    for (PsiElement child : target.getChildren()) {
      if (child instanceof AntElement) {
        ((AntElement)child).acceptAntElementVisitor(this);
      }
    }
  }

  public void visitAntImport(final AntImport antImport) {
    final AntFile antFile = antImport.getImportedFile();
    if (antFile != null) {
      myDependentFiles.add(antFile);
      visitAntFile(antFile);
    }
  }

  public static List<PsiFile> defineProperties(AntFile file) {
    final AntProject project = file.getAntProject();
    LOG.assertTrue(project != null);
    

    final PropertiesBuilder builder = new PropertiesBuilder(file);
    file.acceptAntElementVisitor(builder);

    for (AntTarget target : builder.myVisitedTargets) {
      if (target instanceof AntTargetImpl) {
        definePseudoProperties((AntTargetImpl)target);
      }
    }
    return builder.myDependentFiles;
  }

  private static void definePseudoProperties(final AntTargetImpl target) {
    final XmlTag se = target.getSourceElement();
    XmlAttribute propNameAttribute = se.getAttribute(AntFileImpl.IF_ATTR, null);
    if (propNameAttribute == null) {
      propNameAttribute = se.getAttribute(AntFileImpl.UNLESS_ATTR, null);
    }
    if (propNameAttribute != null) {
      final XmlAttributeValue valueElement = propNameAttribute.getValueElement();
      if (valueElement != null) {
        final String value = target.computeAttributeValue(valueElement.getValue());
        final AntFile propertyHolder = target.getAntFile();
        if (propertyHolder.getProperty(value) == null) {
          target.setPropertyDefinitionElement(valueElement);
          propertyHolder.setProperty(value, target);
        }
      }
    }
  }
  
}
