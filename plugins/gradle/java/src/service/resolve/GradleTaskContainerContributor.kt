// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.openapi.externalSystem.model.task.TaskData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_TASK_CONTAINER
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createType
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessMethods
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessProperties

class GradleTaskContainerContributor : NonCodeMembersContributor() {

  override fun getParentClassName(): String = GRADLE_API_TASK_CONTAINER

  override fun processDynamicElements(qualifierType: PsiType,
                                      aClass: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {
    if (qualifierType !is GradleProjectAwareType) return

    val processProperties = processor.shouldProcessProperties()
    val processMethods = processor.shouldProcessMethods()
    if (!processProperties && !processMethods) {
      return
    }

    val file = place.containingFile ?: return
    val data = GradlePropertyExtensionsContributor.getExtensionsFor(file) ?: return

    val name = processor.getName(state)
    val gradleProjectType = JavaPsiFacade.getInstance(place.project).findClass(GradleCommonClassNames.GRADLE_API_PROJECT,
                                                                               place.resolveScope)
    if (name in (gradleProjectType?.methods?.map(PsiMethod::getName) ?: emptyList())) {
      return
    }
    val tasks = if (name == null) data.tasksMap.values else listOfNotNull(data.tasksMap[name])

    val declaredTasks = if (tasks.isNotEmpty()) findModuleTasks(place) else emptyList()

    for (task in tasks) {
      val description = declaredTasks.find { task.name == it.name && task.typeFqn == it.type }?.description
      if (!processTask(task.name, description, task.typeFqn, file, processProperties, processor, state, processMethods)) return
    }
  }

  private fun processTask(name: String,
                          description: String?,
                          typeFqn: String,
                          file: PsiFile,
                          processProperties: Boolean,
                          processor: PsiScopeProcessor,
                          state: ResolveState,
                          processMethods: Boolean): Boolean {
    val closureType = createType(GROOVY_LANG_CLOSURE, file)
    val taskType = createType(typeFqn, file)
    if (processProperties) {
      val property = GradleTaskProperty(name, typeFqn, description, file)
      return processor.execute(property, state)
    }
    if (processMethods) {
      val method = GrLightMethodBuilder(file.manager, name).apply {
        originInfo = GRADLE_TASK_INFO
        returnType = taskType
        addParameter("configuration", closureType)
      }
      return processor.execute(method, state)
    }
    return true
  }

  private fun findModuleTasks(place: PsiElement): Collection<TaskData> {
    val module = ModuleUtilCore.findModuleForPsiElement(place) ?: return emptyList()
    val externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return emptyList()
    return ExternalSystemApiUtil.findProjectTasks(place.project, GradleConstants.SYSTEM_ID, externalProjectPath)
  }

  companion object {
    internal const val GRADLE_TASK_INFO = "by Gradle tasks"
  }
}
