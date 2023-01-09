// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage.view

import com.intellij.coverage.CoverageBundle
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*


/**
 * This node aims to avoid long [getValue] invocations. Namely, type of node (class/package) and fqn are used
 * for fast [com.intellij.coverage.JavaCoverageAnnotator] requests.
 */
class JavaCoverageNode(project: Project,
                       classOrPackage: PsiNamedElement,
                       bundle: CoverageSuitesBundle,
                       stateBean: CoverageViewManager.StateBean) : CoverageListNode(project, classOrPackage, bundle, stateBean, classOrPackage is PsiClass) {
  init {
    require(classOrPackage is PsiClass || classOrPackage is PsiPackage)
  }

  val qualifiedName: String = ReadAction.compute<String, Throwable> {
    (classOrPackage as? PsiQualifiedNamedElement)?.qualifiedName ?: classOrPackage.name ?: ""
  }

  override fun getWeight() = if (isLeaf) 40 else 30
}

class JavaCoverageRootNode(project: Project,
                           classOrPackage: PsiNamedElement,
                           bundle: CoverageSuitesBundle,
                           stateBean: CoverageViewManager.StateBean) : CoverageListRootNode(project, classOrPackage, bundle, stateBean) {
  override fun update(presentation: PresentationData) {
    ReadAction.run<RuntimeException> {
      presentation.presentableText = CoverageBundle.message("coverage.view.packages.root")
      presentation.setIcon((value as PsiElement).getIcon(0))
    }
  }
}
