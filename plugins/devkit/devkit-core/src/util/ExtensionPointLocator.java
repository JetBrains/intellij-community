// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.With;
import org.jetbrains.idea.devkit.dom.index.ExtensionPointClassIndex;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Locates EP via associated class.
 */
public final class ExtensionPointLocator {
  private final PsiClass myPsiClass;

  public ExtensionPointLocator(PsiClass psiClass) {
    myPsiClass = psiClass;
  }

  public Set<ExtensionPointCandidate> findDirectCandidates() {
    Set<ExtensionPointCandidate> candidates = new HashSet<>();
    findExtensionPointCandidates(myPsiClass, candidates);
    return candidates;
  }

  public Set<ExtensionPointCandidate> findSuperCandidates() {
    Set<ExtensionPointCandidate> candidates = new HashSet<>();
    findExtensionPointCandidatesInHierarchy(myPsiClass, candidates, new HashSet<>());
    return candidates;
  }

  private static void findExtensionPointCandidatesInHierarchy(PsiClass psiClass,
                                                              Set<? super ExtensionPointCandidate> candidates,
                                                              HashSet<? super PsiClass> processed) {
    for (PsiClass superClass : psiClass.getSupers()) {
      if (!processed.add(superClass) || CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) {
        continue;
      }
      findExtensionPointCandidates(superClass, candidates);
      findExtensionPointCandidatesInHierarchy(superClass, candidates, processed);
    }
  }

  private static void findExtensionPointCandidates(PsiClass psiClass, Set<? super ExtensionPointCandidate> candidates) {
    final Project project = psiClass.getProject();
    final SmartPointerManager instance = SmartPointerManager.getInstance(project);

    final List<ExtensionPoint> extensionPoints =
      ExtensionPointClassIndex.getExtensionPointsByClass(project, psiClass, PluginRelatedLocatorsUtils.getCandidatesScope(project));
    for (ExtensionPoint point : extensionPoints) {
      final SmartPsiElementPointer<XmlTag> pointer = instance.createSmartPsiElementPointer(point.getXmlTag());
      final String effectiveQualifiedName = point.getEffectiveQualifiedName();

      if (DomUtil.hasXml(point.getInterface())) {
        candidates.add(new ExtensionPointCandidate(pointer, effectiveQualifiedName));
        continue;
      }

      String tagName = null;
      String attributeName = null;
      for (With element : point.getWithElements()) {
        if (psiClass.equals(element.getImplements().getValue())) {
          tagName = element.getTag().getStringValue();
          attributeName = element.getAttribute().getStringValue();
          break;
        }
      }

      final ExtensionPointCandidate candidate =
        new ExtensionPointCandidate(pointer,
                                    effectiveQualifiedName,
                                    attributeName, tagName,
                                    point.getBeanClass().getStringValue());
      candidates.add(candidate);
    }
  }
}
