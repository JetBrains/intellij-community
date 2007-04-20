/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.*;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 13, 2007
 */
public class PropertiesBuilder extends AntElementVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.psi.impl.PropertiesBuilder");
  @NonNls private static final String DSTAMP = "DSTAMP";
  @NonNls private static final String TSTAMP = "TSTAMP";
  @NonNls private static final String TODAY = "TODAY";

  @NotNull private final AntFile myPropertyHolder;
  private Set<AntTarget> myVisitedTargets = new HashSet<AntTarget>();
  private List<AntTarget> myProjectTargets = new ArrayList<AntTarget>();
  private Set<AntFile> myVisitedFiles = new HashSet<AntFile>();
  
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
    for (PsiElement child : antProject.getChildren()) {
      if (child instanceof AntElement) {
        if (child instanceof AntTarget) {
          final AntTarget antTarget = (AntTarget)child;
          if (myPropertyHolder.equals(antTarget.getAntFile())) {
            // heuristic: do not collect imported targets
            myProjectTargets.add(antTarget);
          }
        }
        else {
          ((AntElement)child).acceptAntElementVisitor(this);
        }
      }
    }
  }

  public void visitAntProperty(final AntProperty antProperty) {
    final PropertiesFile propertiesFile = antProperty.getPropertiesFile();
    if (propertiesFile != null) {
      for (Property property : propertiesFile.getProperties()) {
        myPropertyHolder.setProperty(property.getName(), antProperty);
      }
    }

    if (antProperty instanceof AntPropertyImpl && ((AntPropertyImpl)antProperty).isTstamp()) {
      String prefix = antProperty.getSourceElement().getAttributeValue(AntFileImpl.PREFIX_ATTR);
      if (prefix == null) {
        myPropertyHolder.setProperty(DSTAMP, antProperty);
        myPropertyHolder.setProperty(TSTAMP, antProperty);
        myPropertyHolder.setProperty(TODAY, antProperty);
      }
      else {
        prefix += '.';
        myPropertyHolder.setProperty(prefix + DSTAMP, antProperty);
        myPropertyHolder.setProperty(prefix + TSTAMP, antProperty);
        myPropertyHolder.setProperty(prefix + TODAY, antProperty);
      }
      final XmlAttributeValue value = ((AntPropertyImpl)antProperty).getTstampPropertyAttributeValue();
      if (value != null && value.getValue() != null) {
        myPropertyHolder.setProperty(value.getValue(), antProperty);
      }
    }
    else {
      // non-timestamp property
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
    
    for (PsiElement child : target.getChildren()) {
      if (child instanceof AntElement) {
        ((AntElement)child).acceptAntElementVisitor(this);
      }
    }
  }

  public void visitAntImport(final AntImport antImport) {
    final AntFile antFile = antImport.getImportedFile();
    if (antFile != null) {
      visitAntFile(antFile);
    }
  }

  public static void defineProperties(AntFile file) {
    final AntProject project = file.getAntProject();
    LOG.assertTrue(project != null);
    

    final PropertiesBuilder builder = new PropertiesBuilder(file);
    file.acceptAntElementVisitor(builder);
    
    final AntTarget entryTarget = project.getDefaultTarget();
    if (entryTarget != null) {
      entryTarget.acceptAntElementVisitor(builder);
    }
    
    for (AntTarget antTarget : builder.getUnvisitedTargets()) {
      antTarget.acceptAntElementVisitor(builder);
    }
    
    for (AntTarget target : builder.myVisitedTargets) {
      if (target instanceof AntTargetImpl) {
        definePseudoProperties((AntTargetImpl)target, file);
      }
    }
  }

  private Collection<AntTarget> getUnvisitedTargets() {
    final Set<AntTarget> unvisited = new HashSet<AntTarget>(myProjectTargets);
    unvisited.removeAll(myVisitedTargets);
    return unvisited;
  }

  private static void definePseudoProperties(AntTargetImpl target, final AntFile propertyHolder) {
    final XmlTag se = target.getSourceElement();
    XmlAttribute propNameAttribute = se.getAttribute(AntFileImpl.IF_ATTR, null);
    if (propNameAttribute == null) {
      propNameAttribute = se.getAttribute(AntFileImpl.UNLESS_ATTR, null);
    }
    if (propNameAttribute != null) {
      final XmlAttributeValue valueElement = propNameAttribute.getValueElement();
      if (valueElement != null) {
        final String value = valueElement.getValue();
        if (propertyHolder.getProperty(value) == null) {
          target.setPropertyDefinitionElement(valueElement);
          propertyHolder.setProperty(value, target);
        }
      }
    }
  }
  
}
