// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage.view

import com.intellij.coverage.CoverageBundle
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiQualifiedNamedElement
import javax.swing.Icon


/**
 * This node aims to avoid long [getValue] invocations. Namely, type of node (class/package) and fqn are used
 * for fast [com.intellij.coverage.analysis.JavaCoverageAnnotator] requests.
 */
class JavaCoverageNode(
  project: Project,
  classOrPackage: PsiNamedElement,
  bundle: CoverageSuitesBundle,
  stateBean: CoverageViewManager.StateBean,
  private val presentableName: String,
) : CoverageListNode(project, classOrPackage, bundle, stateBean) {
  init {
    require(classOrPackage is PsiClass || classOrPackage is PsiPackage)
  }

  val qualifiedName: String = ReadAction.compute<String, Throwable> {
    (classOrPackage as? PsiQualifiedNamedElement)?.qualifiedName ?: classOrPackage.name ?: ""
  }
  private val isLeaf = classOrPackage is PsiClass
  private val cachedIcon: Icon? by lazy { classOrPackage.getIcon(0) }

  override fun getWeight() = if (isLeaf) 40 else 30
  override fun update(presentation: PresentationData) {
    presentation.presentableText = presentableName
    presentation.setIcon(cachedIcon)
    presentation.forcedTextForeground = getFileStatus().getColor()
  }
}

class JavaCoverageRootNode(project: Project,
                           classOrPackage: PsiNamedElement,
                           bundle: CoverageSuitesBundle,
                           stateBean: CoverageViewManager.StateBean) : CoverageListRootNode(project, classOrPackage, bundle, stateBean) {
  private val cachedIcon: Icon? by lazy { classOrPackage.getIcon(0) }
  override fun update(presentation: PresentationData) {
    presentation.presentableText = CoverageBundle.message("coverage.view.packages.root")
    presentation.setIcon(cachedIcon)
  }
}
